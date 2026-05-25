package system.assessment.defense.application.manage;

import com.alicp.jetcache.anno.CacheRefresh;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * @Author taoHouChao
 * @Date 23:22 2025/5/17
 */
@Component
@Slf4j
public class OssManager {

    @Resource
    private OSS ossClient;

    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    @Value("${aliyun.oss.bucketName}")
    private String bucketName;

    public String uploadFile(MultipartFile file) {
        //获取原生文件名
        String originalFilename = file.getOriginalFilename();
        String uploadFileName = getFileName(originalFilename);
        // 设置成在线预览
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentDisposition("inline");
        try {
            PutObjectResult putObjectResult = ossClient.putObject(bucketName, uploadFileName, file.getInputStream(), objectMetadata);
            if (putObjectResult != null) {
                String url = "https://" + bucketName + "." + endpoint + "/" + uploadFileName;
                log.info("upload url:[{}]", url);
                return uploadFileName;
            }
        } catch (IOException e) {
            log.error("上传文件失败,e", e);
        }
        return null;
    }

    public String uploadVoice(byte[] voiceData) {
        // 将字节数组转换为输入流
        ByteArrayInputStream inputStream = new ByteArrayInputStream(voiceData);
        // 设置对象元数据
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(voiceData.length);
        metadata.setContentType("audio/wav"); // 根据实际音频格式设置

        String uploadFileName = getFileName(UUID.randomUUID() + ".wav");
        try {
            PutObjectResult putObjectResult = ossClient.putObject(bucketName, uploadFileName, inputStream, metadata);
            if (putObjectResult != null) {
                String url = "https://" + bucketName + "." + endpoint + "/" + uploadFileName;
                log.info("upload url:[{}]", url);
                return uploadFileName;
            }
        } catch (Exception e) {
            log.error("上传文件失败,e", e);
        }
        return null;
    }

    public String getFileName(String originalFilename) {
        DateTimeFormatter dft = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        LocalDateTime time = LocalDateTime.now();
        //拼装OSS上存储的路径
        String folder = dft.format(time);
        String fileName = generateUUID();
        // 扩展名
        if (originalFilename.contains(".")) {
            String extension = Optional.of(originalFilename).map(item -> item.substring(item.lastIndexOf("."))).orElse("");
            //在OSS上bucket下的文件名
            return "defense-assessment-new/" + folder + "/" + fileName + extension;
        }else {
            return "defense-assessment-new/" + folder + "/" + fileName;
        }
    }

    private String generateUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "").substring(0, 32);
    }

    @Cached(name = "oss_url_", key = "#url",
            expire = 600, localExpire = 300,
            localLimit = 1000,
            cacheType = CacheType.BOTH)
    @CacheRefresh(refresh = 100, stopRefreshAfterLastAccess = 3600)
    public String getSignedUrl(String url) {
        // 设置URL过期时间（单位：毫秒，建议30分钟）
        Date expiration = new Date(System.currentTimeMillis() + 60 * 60 * 1000);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, url, HttpMethod.GET);
        request.setExpiration(expiration);
        String httpUrl = ossClient.generatePresignedUrl(request).toString();
        return httpUrl.replace("http://", "https://");
    }

    public String getSignedPutUrl(String url, String contentType) {
        // 设置URL过期时间（单位：毫秒，建议30分钟）现在设置为6小时
        Date expiration = new Date(System.currentTimeMillis() + 6 * 60 * 60 * 1000);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, url, HttpMethod.PUT);
        request.setContentType(contentType);
        request.setExpiration(expiration);
        String httpUrl = ossClient.generatePresignedUrl(request).toString();
        return httpUrl.replace("http://", "https://");
    }

    public void preview(String fileKey, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {

            // 1. 获取文件元信息
            ObjectMetadata metadata;
            try {
                metadata = ossClient.getObjectMetadata(bucketName, fileKey);
            } catch (OSSException e) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在");
                return;
            }

            long fileSize = metadata.getContentLength();
            String contentType = metadata.getContentType();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "application/pdf"; // 默认 PDF
            }

            // 2. 解析 Range 请求
            String rangeHeader = request.getHeader("Range");
            long start = 0, end = fileSize - 1;
            boolean isPartial = false;

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                isPartial = true;
                String[] ranges = rangeHeader.substring(6).split("-");
                try {
                    start = ranges.length > 0 && !ranges[0].isEmpty() ? Long.parseLong(ranges[0]) : 0;
                    end = ranges.length > 1 && !ranges[1].isEmpty() ? Long.parseLong(ranges[1]) : fileSize - 1;
                } catch (NumberFormatException e) {
                    response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    response.setHeader("Content-Range", "bytes */" + fileSize);
                    return;
                }

                // 校验范围合法性
                start = Math.max(0, start);
                end = Math.min(end, fileSize - 1);

                if (start > end) {
                    response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    response.setHeader("Content-Range", "bytes */" + fileSize);
                    return;
                }
            }

            // 3. 设置响应头
            response.setContentType(contentType);
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Content-Length", String.valueOf(end - start + 1));

            if (isPartial) {
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT); // 206
                response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
            } else {
                response.setStatus(HttpServletResponse.SC_OK); // 200
                // 支持中文文件名
                String fileName = fileKey.substring(fileKey.lastIndexOf("/") + 1);
                response.setHeader("Content-Disposition",
                        "inline; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
            }

            // 4. 从 OSS 获取指定范围的数据
            GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, fileKey);
            getObjectRequest.setRange(start, end);
            OSSObject ossObject = ossClient.getObject(getObjectRequest);
            InputStream inputStream = ossObject.getObjectContent();

            // 5. 写出到响应流
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                response.getOutputStream().write(buffer, 0, bytesRead);
            }
            response.flushBuffer();

            // 6. 关闭资源
            inputStream.close();
            ossObject.close();

        } catch (Exception e) {
            // 日志记录
            log.error("文件预览失败", e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "服务器内部错误");
            }
        }
    }
}
