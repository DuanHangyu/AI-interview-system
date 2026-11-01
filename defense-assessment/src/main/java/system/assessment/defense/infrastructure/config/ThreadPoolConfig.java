package system.assessment.defense.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {

    /**
     * 通用的短任务线程池（导入学生后补充关联、考试材料预解析等）。
     * 有界队列 + CallerRuns：队列满时由提交线程自己执行，起到反压作用，不会丢任务也不会无限堆积。
     * 大模型相关的长任务不要放这里，统一走持久化任务队列（TaskWorker）。
     */
    @Bean("threadPoolExecutor")
    public ThreadPoolExecutor threadPoolExecutor() {
        int core = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolExecutor(core, core * 2, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500),
                Thread.ofPlatform().name("app-pool-", 0).factory(), new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
