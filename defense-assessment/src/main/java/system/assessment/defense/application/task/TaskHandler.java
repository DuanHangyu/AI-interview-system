package system.assessment.defense.application.task;

/**
 * 一类后台任务的处理器。处理器必须是幂等的：租约过期、实例崩溃都可能导致同一任务被再次执行。
 */
public interface TaskHandler {

    String type();

    /**
     * 单实例内同时执行的最大数量
     */
    int concurrency();

    default int maxAttempts() {
        return 5;
    }

    /**
     * 单次执行的租约时长，超过后任务可被其他实例重新抢占；应明显大于正常执行耗时
     */
    default int leaseSeconds() {
        return 300;
    }

    void handle(Integer bizId) throws Exception;

    /**
     * 达到最大重试次数后调用，用于写入兜底结果，避免下游任务永远等待
     */
    default void onGiveUp(Integer bizId, Exception lastError) {
    }

    /**
     * 第 n 次失败后的重试间隔（秒）：15s、30s、60s、120s…，上限 10 分钟
     */
    default int retryDelaySeconds(int attempts) {
        return (int) Math.min(600, 15L << Math.max(0, Math.min(attempts - 1, 10)));
    }

    /**
     * 处理器发现依赖尚未就绪时抛出，任务会在 delaySeconds 后再次执行且不计入重试次数
     */
    class DeferException extends RuntimeException {

        private final int delaySeconds;

        public DeferException(String reason, int delaySeconds) {
            super(reason);
            this.delaySeconds = delaySeconds;
        }

        public int getDelaySeconds() {
            return delaySeconds;
        }
    }
}
