package system.assessment.defense.application.manage;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class AiCallExceptionTest {

    @Test
    void onlyStatus429IsRateLimited() {
        assertTrue(AiCallException.ofStatus(429, "limit").isRateLimited());
        assertTrue(AiCallException.ofStatus(429, "limit").isRetryable());
        assertFalse(AiCallException.ofStatus(500, "err").isRateLimited());
        assertFalse(AiCallException.ofStatus(400, "bad").isRetryable());
        assertFalse(AiCallException.ofIo(new IOException("timeout")).isRateLimited());
    }

    @Test
    void rateLimitIsFoundThroughWrappedCauses() {
        Exception wrapped = new IllegalStateException("analysis failed", new RuntimeException(AiCallException.ofStatus(429, "x")));
        assertTrue(AiCallException.isRateLimited(wrapped));
        assertFalse(AiCallException.isRateLimited(new RuntimeException(AiCallException.ofStatus(503, "x"))));
        assertFalse(AiCallException.isRateLimited(null));
    }
}
