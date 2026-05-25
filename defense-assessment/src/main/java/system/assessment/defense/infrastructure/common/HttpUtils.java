package system.assessment.defense.infrastructure.common;

import com.alicp.jetcache.anno.CacheRefresh;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import system.assessment.defense.application.manage.OssManager;

import java.io.IOException;

/**
 * @USER taoHouChao
 * @DATE 08:23 2025/8/15
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HttpUtils {

    private final OkHttpClient okHttpClient;

    private final OssManager ossManger;

    @Cached(name = "file_",
            key = "#fileUrl",
            localExpire = 300, localLimit = 1000,
            expire = 600,
            cacheType = CacheType.BOTH)
    public FileByteDTO downloadFile(String fileUrl) {
        String signedUrl = ossManger.getSignedUrl(fileUrl);
        Request request = new Request.Builder()
                .url(signedUrl)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download image: " + response);
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Response body is null");
            }
            byte[] bytes = body.bytes();
            log.info("download file success fileUrl:{}, fileSize:{}", fileUrl, bytes.length);
            return new FileByteDTO(bytes);
        } catch (Exception e) {
            log.error("Failed to download image, fileUrl:{}", fileUrl, e);
            return null;
        }
    }

    @Cached(name = "file_",
            key = "#fileUrl",
            localExpire = 300, localLimit = 1000,
            expire = 600,
            cacheType = CacheType.BOTH)
    @CacheRefresh(refresh = 150, stopRefreshAfterLastAccess = 7200)
    public FileByteDTO downloadQuestionVoice(String fileUrl) {
        String signedUrl = ossManger.getSignedUrl(fileUrl);
        Request request = new Request.Builder()
                .url(signedUrl)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download image: " + response);
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Response body is null");
            }
            byte[] bytes = body.bytes();
            log.info("Download file success fileUrl:{}, fileSize:{}", fileUrl, bytes.length);
            return new FileByteDTO(bytes);
        } catch (IOException e) {
            log.error("Failed to download image", e);
            return null;
        }
    }
}
