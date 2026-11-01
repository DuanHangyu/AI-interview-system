package system.assessment.defense.infrastructure.storage;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Date;

/**
 * 阿里云 OSS 存储。
 */
@Slf4j
public class OssFileStorage implements FileStorage {

    private static final long URL_TTL_MILLIS = 60 * 60 * 1000;

    private final OSS ossClient;
    private final String bucketName;

    public OssFileStorage(OSS ossClient, String bucketName) {
        this.ossClient = ossClient;
        this.bucketName = bucketName;
        log.info("using aliyun oss storage, bucket:{}", bucketName);
    }

    @Override
    public String put(String folder, String originalName, byte[] data, String contentType) {
        String key = FileStorage.newKey(folder, originalName);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(data.length);
        metadata.setContentType(StringUtils.defaultIfBlank(contentType, FileStorage.guessContentType(key)));
        metadata.setContentDisposition("inline");
        ossClient.putObject(bucketName, key, new ByteArrayInputStream(data), metadata);
        return key;
    }

    @Override
    public byte[] read(String key) {
        try (OSSObject object = ossClient.getObject(bucketName, key);
             InputStream in = object.getObjectContent()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("读取文件失败: " + key, e);
        }
    }

    @Override
    public String signedUrl(String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, key, HttpMethod.GET);
        request.setExpiration(new Date(System.currentTimeMillis() + URL_TTL_MILLIS));
        return ossClient.generatePresignedUrl(request).toString().replace("http://", "https://");
    }

    @Override
    public void serve(String key, HttpServletRequest request, HttpServletResponse response) throws IOException {
        ObjectMetadata metadata;
        try {
            metadata = ossClient.getObjectMetadata(bucketName, key);
        } catch (OSSException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在");
            return;
        }
        String contentType = StringUtils.defaultIfBlank(metadata.getContentType(), FileStorage.guessContentType(key));
        RangeResponder.write(key, metadata.getContentLength(), contentType, request, response, (start, end) -> {
            GetObjectRequest get = new GetObjectRequest(bucketName, key);
            get.setRange(start, end);
            OSSObject object = ossClient.getObject(get);
            return new FilterInputStream(object.getObjectContent()) {
                @Override
                public void close() throws IOException {
                    super.close();
                    object.close();
                }
            };
        });
    }
}
