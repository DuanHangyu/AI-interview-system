package system.assessment.defense.infrastructure.storage;

import com.aliyun.oss.OSSClientBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * storage.type = oss | local | auto（默认，配置了 OSS AccessKey 就用 OSS，否则用本地磁盘）
 */
@Configuration
public class StorageConfig {

    @Bean
    public FileStorage fileStorage(@Value("${storage.type:auto}") String type,
                                   @Value("${aliyun.oss.endpoint:}") String endpoint,
                                   @Value("${aliyun.oss.accessKeyId:}") String accessKeyId,
                                   @Value("${aliyun.oss.accessKeySecret:}") String accessKeySecret,
                                   @Value("${aliyun.oss.bucketName:}") String bucketName,
                                   @Value("${storage.local.dir:./data/storage}") String localDir,
                                   @Value("${storage.local.public-url-prefix:}") String publicUrlPrefix,
                                   @Value("${storage.local.secret:change-me-in-production}") String secret) {
        boolean useOss = "oss".equalsIgnoreCase(type)
                || ("auto".equalsIgnoreCase(type) && StringUtils.isNoneBlank(accessKeyId, accessKeySecret));
        if (useOss) {
            return new OssFileStorage(new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret), bucketName);
        }
        return new LocalFileStorage(localDir, publicUrlPrefix, secret);
    }
}
