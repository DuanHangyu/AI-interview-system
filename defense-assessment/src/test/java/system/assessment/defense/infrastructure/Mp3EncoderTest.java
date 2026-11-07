package system.assessment.defense.infrastructure;

import org.junit.jupiter.api.Test;
import system.assessment.defense.infrastructure.common.Mp3Encoder;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Mp3EncoderTest {

    @Test
    void encodesSpeechWavToMuchSmallerMp3() {
        byte[] wav = sineWav(24000, 10);
        Mp3Encoder.Audio audio = Mp3Encoder.fromWav(wav);
        assertEquals(Mp3Encoder.MP3_TYPE, audio.contentType());
        assertTrue(audio.data().length < wav.length / 6, "mp3 " + audio.data().length + " wav " + wav.length);
        // MP3 帧同步字
        assertEquals(0xFF, audio.data()[0] & 0xFF);
        assertSame(audio.data(), Mp3Encoder.fromWav(wav).data());
    }

    @Test
    void fallsBackToOriginalForNonWav() {
        byte[] junk = new byte[100];
        Mp3Encoder.Audio audio = Mp3Encoder.fromWav(junk);
        assertEquals(Mp3Encoder.WAV_TYPE, audio.contentType());
        assertSame(junk, audio.data());
    }

    private static byte[] sineWav(int rate, int seconds) {
        int samples = rate * seconds;
        ByteBuffer pcm = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples; i++) {
            pcm.putShort((short) (Math.sin(2 * Math.PI * 440 * i / rate) * 8000));
        }
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes()).putInt(36 + samples * 2).put("WAVE".getBytes())
                .put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(samples * 2);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(h.array());
        out.writeBytes(pcm.array());
        return out.toByteArray();
    }
}
