package system.assessment.defense.infrastructure.config;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class OkHttpConfig {

    /**
     * 调用百炼的 HTTP 客户端。超时要短到能及时失败、长到容纳一次长文本评分；
     * 失败由调用方按退避策略重试，而不是让请求挂起几分钟占住线程。
     */
    @Bean
    public OkHttpClient okHttpClient(@Value("${http-client.connect-timeout-seconds:10}") long connectTimeout,
                                     @Value("${http-client.read-timeout-seconds:180}") long readTimeout,
                                     @Value("${http-client.call-timeout-seconds:240}") long callTimeout) {
        return new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(64, 5, TimeUnit.MINUTES))
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(callTimeout, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                // 只用 HTTP/1.1：HTTP/2 会把所有并发请求复用到同一条 TCP 连接上，
                // 多个几百 KB 的音频请求同时上传时互相抢占流控窗口，导致大量超时
                .protocols(List.of(Protocol.HTTP_1_1))
                .build();
    }
}
