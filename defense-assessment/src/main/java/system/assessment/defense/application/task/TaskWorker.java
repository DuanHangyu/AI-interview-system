package system.assessment.defense.application.task;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import system.assessment.defense.application.manage.AiRateLimiter;
import system.assessment.defense.infrastructure.repository.dao.mapper.AsyncTaskMapper;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 轮询数据库中的到期任务并执行。
 * <ul>
 *     <li>多实例安全：通过条件 UPDATE 抢占，同一任务只会被一个实例执行；</li>
 *     <li>崩溃恢复：执行中的任务带租约，实例宕机后租约过期，任务会被其他实例接手；</li>
 *     <li>削峰：每类任务有独立的并发上限，只领取有空闲线程能处理的数量，高峰期任务在库里排队而不是堆在内存里。</li>
 * </ul>
 */
@Slf4j
@Component
public class TaskWorker {

    private final AsyncTaskMapper taskMapper;
    private final boolean enabled;
    private final Set<String> enabledTypes;
    private final String owner;
    private final Map<String, Lane> lanes = new HashMap<>();

    public TaskWorker(AsyncTaskMapper taskMapper, List<TaskHandler> handlers,
                      @Value("${task.worker.enabled:true}") boolean enabled,
                      @Value("${task.worker.types:}") String types) {
        this.taskMapper = taskMapper;
        this.enabled = enabled;
        // 为空表示处理所有类型；也可以只在部分实例上处理某些任务（例如专门的转写节点）
        this.enabledTypes = Arrays.stream(StringUtils.defaultString(types).split(","))
                .map(String::trim).filter(StringUtils::isNotEmpty).collect(Collectors.toSet());
        this.owner = StringUtils.abbreviate(ManagementFactory.getRuntimeMXBean().getName(), 40)
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        for (TaskHandler handler : handlers) {
            ExecutorService executor = Executors.newFixedThreadPool(handler.concurrency(),
                    Thread.ofPlatform().name("task-" + handler.type().toLowerCase() + "-", 0).factory());
            lanes.put(handler.type(), new Lane(handler, new Semaphore(handler.concurrency()), executor));
        }
        log.info("task worker owner:{}, enabled:{}, lanes:{}, types:{}", owner, enabled, lanes.keySet(),
                enabledTypes.isEmpty() ? "ALL" : enabledTypes);
    }

    @Scheduled(fixedDelayString = "${task.worker.poll-interval-ms:1000}", initialDelay = 3000)
    public void poll() {
        if (!enabled) {
            return;
        }
        for (Lane lane : lanes.values()) {
            if (!enabledTypes.isEmpty() && !enabledTypes.contains(lane.handler().type())) {
                continue;
            }
            try {
                dispatch(lane);
            } catch (Exception e) {
                log.error("poll task failed, type:{}", lane.handler().type(), e);
            }
        }
    }

    private void dispatch(Lane lane) {
        int free = lane.permits().availablePermits();
        if (free <= 0) {
            return;
        }
        TaskHandler handler = lane.handler();
        for (Long id : taskMapper.findDue(handler.type(), free)) {
            if (!lane.permits().tryAcquire()) {
                return;
            }
            if (taskMapper.claim(id, owner, handler.leaseSeconds()) != 1) {
                lane.permits().release();
                continue;
            }
            lane.executor().execute(() -> {
                try {
                    run(handler, id);
                } finally {
                    lane.permits().release();
                }
            });
        }
    }

    private void run(TaskHandler handler, Long id) {
        var task = taskMapper.selectById(id);
        if (task == null) {
            return;
        }
        long start = System.currentTimeMillis();
        try {
            handler.handle(task.getBizId());
            taskMapper.complete(id, owner);
            log.info("task done, type:{}, bizId:{}, attempt:{}, cost:{}ms",
                    handler.type(), task.getBizId(), task.getAttempts(), System.currentTimeMillis() - start);
        } catch (TaskHandler.DeferException e) {
            taskMapper.defer(id, owner, e.getDelaySeconds());
        } catch (AiRateLimiter.AiBusyException e) {
            // 本实例的大模型并发已满，不是任务本身的问题，稍后再试
            taskMapper.defer(id, owner, 5);
            log.debug("task deferred, type:{}, bizId:{}, reason:{}", handler.type(), task.getBizId(), e.getMessage());
        } catch (Exception e) {
            int attempts = task.getAttempts() == null ? 1 : task.getAttempts();
            boolean giveUp = attempts >= task.getMaxAttempts();
            taskMapper.fail(id, owner, StringUtils.abbreviate(String.valueOf(e.getMessage()), 1000),
                    handler.retryDelaySeconds(attempts));
            if (giveUp) {
                log.error("task give up, type:{}, bizId:{}, attempts:{}", handler.type(), task.getBizId(), attempts, e);
                try {
                    handler.onGiveUp(task.getBizId(), e);
                } catch (Exception ex) {
                    log.error("task onGiveUp failed, type:{}, bizId:{}", handler.type(), task.getBizId(), ex);
                }
            } else {
                log.warn("task failed, will retry, type:{}, bizId:{}, attempts:{}, error:{}",
                        handler.type(), task.getBizId(), attempts, e.getMessage());
            }
        }
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        for (Lane lane : lanes.values()) {
            lane.executor().shutdown();
        }
        for (Lane lane : lanes.values()) {
            // 未完成的任务租约到期后会被重新执行，这里只做有限等待
            lane.executor().awaitTermination(20, TimeUnit.SECONDS);
        }
    }

    private record Lane(TaskHandler handler, Semaphore permits, ExecutorService executor) {
    }
}
