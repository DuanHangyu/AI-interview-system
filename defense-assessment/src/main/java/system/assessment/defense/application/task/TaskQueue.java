package system.assessment.defense.application.task;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import system.assessment.defense.infrastructure.repository.dao.mapper.AsyncTaskMapper;
import system.assessment.defense.infrastructure.repository.dao.po.AsyncTaskPO;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class TaskQueue {

    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final AsyncTaskMapper taskMapper;

    /**
     * 入队。写入与业务数据处于同一事务中，业务回滚时任务也不会残留。
     */
    public void enqueue(String type, Integer bizId) {
        enqueue(type, bizId, 0);
    }

    public void enqueue(String type, Integer bizId, int delaySeconds) {
        taskMapper.upsert(type, bizId, DEFAULT_MAX_ATTEMPTS, delaySeconds);
    }

    /**
     * 事务提交后再入队，用于不希望任务在业务事务中抢先执行的场景（例如任务需要读取刚提交的数据）。
     */
    public void enqueueAfterCommit(String type, Integer bizId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            enqueue(type, bizId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                enqueue(type, bizId);
            }
        });
    }

    public boolean isRunning(String type, Integer bizId) {
        return Objects.equals(taskMapper.findStatus(type, bizId), AsyncTaskPO.RUNNING);
    }
}
