package system.assessment.defense.application.manage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import system.assessment.defense.infrastructure.exception.BusinessException;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 进程内的大模型调用并发闸门。
 * <p>
 * 百炼按 RPM / 并发限流，超出后返回 429；高峰期如果所有请求同时打出去，会出现大面积失败和重试风暴。
 * 这里按能力（文本大模型 / 语音识别 / 语音合成）分别限制同时在途的请求数，排队超时则快速失败，
 * 由调用方（后台任务会自动退避重试）决定如何处理。多实例部署时总并发 = 单实例配置 × 实例数。
 */
@Slf4j
@Component
public class AiRateLimiter {

    public enum Capability {LLM, ASR, TTS}

    private final Semaphore llm;
    private final Semaphore asr;
    private final Semaphore tts;
    private final long acquireTimeoutMs;

    public AiRateLimiter(@Value("${dashscope.concurrency.llm:6}") int llmPermits,
                         @Value("${dashscope.concurrency.asr:10}") int asrPermits,
                         @Value("${dashscope.concurrency.tts:6}") int ttsPermits,
                         @Value("${dashscope.concurrency.acquire-timeout-ms:60000}") long acquireTimeoutMs) {
        this.llm = new Semaphore(llmPermits, true);
        this.asr = new Semaphore(asrPermits, true);
        this.tts = new Semaphore(ttsPermits, true);
        this.acquireTimeoutMs = acquireTimeoutMs;
    }

    /**
     * 本地并发闸门已满。后台任务遇到它会延后重试且不计入失败次数。
     */
    public static class AiBusyException extends BusinessException {
        public AiBusyException() {
            super(503, "系统繁忙，请稍后重试");
        }
    }

    public <T> T call(Capability capability, Supplier<T> action) {
        Semaphore semaphore = switch (capability) {
            case LLM -> llm;
            case ASR -> asr;
            case TTS -> tts;
        };
        boolean acquired;
        try {
            acquired = semaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiBusyException();
        }
        if (!acquired) {
            log.warn("AI {} 并发已满，排队超时 {}ms", capability, acquireTimeoutMs);
            throw new AiBusyException();
        }
        try {
            return action.get();
        } finally {
            semaphore.release();
        }
    }
}
