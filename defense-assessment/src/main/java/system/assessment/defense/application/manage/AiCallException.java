package system.assessment.defense.application.manage;

import lombok.Getter;

import java.io.IOException;

/**
 * 调用百炼接口失败。retryable 表示网络错误、限流(429)或服务端错误(5xx)，值得退避后重试。
 */
@Getter
public class AiCallException extends RuntimeException {

    private final boolean retryable;

    /** 429：账号的调用频率配额暂时用完，属于暂时性拥塞而不是请求本身有问题 */
    private final boolean rateLimited;

    private AiCallException(String message, boolean retryable, Throwable cause) {
        this(message, retryable, false, cause);
    }

    private AiCallException(String message, boolean retryable, boolean rateLimited, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.rateLimited = rateLimited;
    }

    public static AiCallException ofStatus(int status, String body) {
        boolean retryable = status == 429 || status >= 500;
        String brief = body == null ? "" : body.substring(0, Math.min(body.length(), 500));
        return new AiCallException("API call failed: " + status + " - " + brief, retryable, status == 429, null);
    }

    /** 异常链中是否有限流（429）错误 */
    public static boolean isRateLimited(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof AiCallException ace && ace.isRateLimited()) {
                return true;
            }
        }
        return false;
    }

    public static AiCallException ofIo(IOException e) {
        return new AiCallException("API call io error: " + e.getMessage(), true, e);
    }

    public static AiCallException of(String message) {
        return new AiCallException(message, false, null);
    }

    /**
     * 指数退避：1s、2s、4s... 上限 8s，加少量抖动避免多个请求同时重试。
     */
    public static void backoff(int attempt) {
        long base = Math.min(8000L, 1000L << Math.max(0, attempt - 1));
        long jitter = (long) (Math.random() * 300);
        try {
            Thread.sleep(base + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiCallException("interrupted", false, e);
        }
    }
}
