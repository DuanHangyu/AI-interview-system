package system.assessment.defense.infrastructure.storage;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件存储。数据库中只保存 key，访问时再换成可直接访问的 URL。
 */
public interface FileStorage {

    /**
     * @param folder 逻辑目录，例如 uploads、answers、tts
     * @return 文件 key
     */
    String put(String folder, String originalName, byte[] data, String contentType);

    byte[] read(String key);

    /**
     * 浏览器可直接访问的临时 URL（约 1 小时有效）
     */
    String signedUrl(String key);

    /**
     * 以支持 Range 的方式把文件写到响应中（音频拖动进度、PDF 分段加载）
     */
    void serve(String key, HttpServletRequest request, HttpServletResponse response) throws IOException;

    static String newKey(String folder, String originalName) {
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
            if (!extension.matches("\\.[a-z0-9]{1,8}")) {
                extension = "";
            }
        }
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return "defense-assessment-new/" + folder + "/" + date + "/" + UUID.randomUUID().toString().replace("-", "") + extension;
    }

    static String guessContentType(String key) {
        String lower = key == null ? "" : key.toLowerCase();
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return "application/octet-stream";
    }
}
