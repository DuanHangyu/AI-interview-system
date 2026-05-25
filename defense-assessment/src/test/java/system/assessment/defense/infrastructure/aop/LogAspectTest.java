package system.assessment.defense.infrastructure.aop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogAspectTest {

    @Test
    void sanitizeForLogMasksSecrets() {
        String content = """
                {"account":"admin","password":"plain-password","token":"raw-token","Authorization":"Bearer abc"}
                """;

        String sanitized = LogAspect.sanitizeForLog(content);

        assertTrue(sanitized.contains("\"password\":\"***\""));
        assertTrue(sanitized.contains("\"token\":\"***\""));
        assertTrue(sanitized.contains("\"Authorization\":\"***\""));
        assertFalse(sanitized.contains("plain-password"));
        assertFalse(sanitized.contains("raw-token"));
        assertFalse(sanitized.contains("Bearer abc"));
    }

    @Test
    void sanitizeForLogTruncatesLongPayloads() {
        String sanitized = LogAspect.sanitizeForLog("a".repeat(1200));

        assertTrue(sanitized.length() < 1100);
        assertTrue(sanitized.contains("truncated,length=1200"));
    }
}
