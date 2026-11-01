package system.assessment.defense.infrastructure.storage;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 处理 HTTP Range 请求的公共逻辑。
 */
final class RangeResponder {

    @FunctionalInterface
    interface RangeOpener {
        InputStream open(long start, long end) throws IOException;
    }

    private RangeResponder() {
    }

    static void write(String key, long size, String contentType, HttpServletRequest request,
                      HttpServletResponse response, RangeOpener opener) throws IOException {
        long start = 0;
        long end = size - 1;
        String range = request.getHeader("Range");
        boolean partial = range != null && range.startsWith("bytes=");
        if (partial) {
            String[] parts = range.substring(6).split("-", 2);
            try {
                if (!parts[0].isEmpty()) {
                    start = Long.parseLong(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        end = Long.parseLong(parts[1]);
                    }
                } else if (parts.length > 1 && !parts[1].isEmpty()) {
                    start = Math.max(0, size - Long.parseLong(parts[1]));
                }
            } catch (NumberFormatException e) {
                start = size;
            }
            end = Math.min(end, size - 1);
            if (start > end) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + size);
                return;
            }
        }
        response.setContentType(contentType);
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Cache-Control", "private, max-age=3600");
        response.setContentLengthLong(end - start + 1);
        if (partial) {
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + size);
        } else {
            String fileName = key.substring(key.lastIndexOf('/') + 1);
            response.setHeader("Content-Disposition",
                    "inline; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
        }
        try (InputStream in = opener.open(start, end)) {
            OutputStream out = response.getOutputStream();
            in.transferTo(out);
            out.flush();
        }
    }
}
