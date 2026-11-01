package system.assessment.defense.infrastructure.common;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WavUtilsTest {

    @Test
    void parsesHeaderAndDuration() {
        WavUtils.WavInfo info = WavUtils.parse(wav(16000, 5.0, false));
        assertThat(info.sampleRate()).isEqualTo(16000);
        assertThat(info.channels()).isEqualTo(1);
        assertThat(info.bitsPerSample()).isEqualTo(16);
        assertThat(info.durationSeconds()).isEqualTo(5.0);
    }

    @Test
    void toleratesPlaceholderDataLengthFromStreamingEncoders() {
        byte[] streaming = wav(24000, 2.0, true);
        assertThat(WavUtils.parse(streaming).durationSeconds()).isEqualTo(2.0);
        byte[] normalized = WavUtils.normalize(streaming);
        assertThat(ByteBuffer.wrap(normalized).order(ByteOrder.LITTLE_ENDIAN).getInt(40)).isEqualTo(24000 * 2 * 2);
    }

    @Test
    void splitsLongRecordingIntoChunks() {
        List<byte[]> chunks = WavUtils.split(wav(16000, 250.0, false), 120);
        assertThat(chunks).hasSize(3);
        assertThat(WavUtils.parse(chunks.get(0)).durationSeconds()).isEqualTo(120.0);
        assertThat(WavUtils.parse(chunks.get(2)).durationSeconds()).isEqualTo(10.0);
    }

    @Test
    void rejectsNonWav() {
        assertThatThrownBy(() -> WavUtils.parse("not a wav file at all, definitely not".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WavUtils.parse(new byte[10])).isInstanceOf(IllegalArgumentException.class);
    }

    static byte[] wav(int sampleRate, double seconds, boolean placeholderLength) {
        int dataLength = (int) (sampleRate * 2 * seconds);
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(placeholderLength ? -1 : 36 + dataLength);
        buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * 2);
        buffer.putShort((short) 2);
        buffer.putShort((short) 16);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(placeholderLength ? -1 : dataLength);
        return buffer.array();
    }
}
