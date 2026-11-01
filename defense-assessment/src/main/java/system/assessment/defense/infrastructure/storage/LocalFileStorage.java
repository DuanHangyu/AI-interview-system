package system.assessment.defense.infrastructure.storage;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 本地磁盘存储，用于私有化部署和开发测试。多实例部署时目录需要挂载到共享存储（NFS 等）。
 * 浏览器通过 /file/raw 的签名 URL 访问，签名防止 key 被枚举。
 */
@Slf4j
public class LocalFileStorage implements FileStorage {

    private static final long URL_TTL_SECONDS = 3600;

    private final Path root;
    private final String publicUrlPrefix;
    private final byte[] secret;

    public LocalFileStorage(String dir, String publicUrlPrefix, String secret) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        this.publicUrlPrefix = StringUtils.removeEnd(StringUtils.defaultString(publicUrlPrefix), "/");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("无法创建存储目录 " + root, e);
        }
        log.info("using local file storage at {}", root);
    }

    @Override
    public String put(String folder, String originalName, byte[] data, String contentType) {
        String key = FileStorage.newKey(folder, originalName);
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(tmp, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return key;
        } catch (IOException e) {
            throw new UncheckedIOException("保存文件失败", e);
        }
    }

    @Override
    public byte[] read(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("读取文件失败: " + key, e);
        }
    }

    @Override
    public String signedUrl(String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        long expires = System.currentTimeMillis() / 1000 + URL_TTL_SECONDS;
        return publicUrlPrefix + "/file/raw?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "&expires=" + expires + "&sig=" + sign(key, expires);
    }

    public boolean verify(String key, long expires, String sig) {
        if (StringUtils.isAnyBlank(key, sig) || expires < System.currentTimeMillis() / 1000) {
            return false;
        }
        return MessageDigest.isEqual(sign(key, expires).getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void serve(String key, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Path file;
        try {
            file = resolve(key);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (!Files.isRegularFile(file)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在");
            return;
        }
        RangeResponder.write(key, Files.size(file), FileStorage.guessContentType(key), request, response,
                (start, end) -> {
                    FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
                    channel.position(start);
                    InputStream in = Channels.newInputStream(channel);
                    return new java.io.FilterInputStream(in) {
                        private long remaining = end - start + 1;

                        @Override
                        public int read() throws IOException {
                            if (remaining <= 0) return -1;
                            int b = super.read();
                            if (b >= 0) remaining--;
                            return b;
                        }

                        @Override
                        public int read(byte[] buf, int off, int len) throws IOException {
                            if (remaining <= 0) return -1;
                            int n = super.read(buf, off, (int) Math.min(len, remaining));
                            if (n > 0) remaining -= n;
                            return n;
                        }
                    };
                });
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || key.contains("..")) {
            throw new IllegalArgumentException("非法的文件 key");
        }
        return path;
    }

    private String sign(String key, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal((key + "\n" + expires).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
