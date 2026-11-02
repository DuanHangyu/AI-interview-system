package system.assessment.defense.infrastructure.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordCodecTest {

    @Test
    void hashedPasswordMatchesOnlyTheOriginal() {
        String hash = PasswordCodec.hash("Secret123");
        assertTrue(PasswordCodec.isHashed(hash));
        assertNotEquals("Secret123", hash);
        assertTrue(PasswordCodec.matches("Secret123", hash));
        assertFalse(PasswordCodec.matches("secret123", hash));
    }

    @Test
    void legacyPlainTextStillMatches() {
        assertFalse(PasswordCodec.isHashed("teacher123"));
        assertTrue(PasswordCodec.matches("teacher123", "teacher123"));
        assertFalse(PasswordCodec.matches("teacher12", "teacher123"));
        assertFalse(PasswordCodec.matches(null, "teacher123"));
        assertFalse(PasswordCodec.matches("x", null));
    }

    @Test
    void blankMeansUnchanged() {
        assertNull(PasswordCodec.hashOrNull(null));
        assertNull(PasswordCodec.hashOrNull("  "));
        assertTrue(PasswordCodec.isHashed(PasswordCodec.hashOrNull("abc123")));
    }
}
