package system.assessment.defense.infrastructure.common;

import de.sciss.jump3r.lowlevel.LameEncoder;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把题目播报的 PCM WAV 转成单声道 MP3（约为原来的十分之一）。
 * 考试开场时几十名考生同时下载题目语音，WAV 会占满服务器出口带宽导致下载超时。
 * 题库音频是固定的，转好的结果按内容缓存，同一道题只转一次；转换失败时原样返回 WAV。
 */
public final class Mp3Encoder {

    public static final String WAV_TYPE = "audio/wav";
    public static final String MP3_TYPE = "audio/mpeg";

    private static final int BIT_RATE = 48;
    private static final int CACHE_SIZE = 128;

    private static final Map<String, byte[]> CACHE = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private Mp3Encoder() {
    }

    public record Audio(byte[] data, String contentType) {
    }

    public static Audio fromWav(byte[] wav) {
        String key = wav.length + ":" + Arrays.hashCode(wav);
        byte[] cached;
        synchronized (CACHE) {
            cached = CACHE.get(key);
        }
        if (cached != null) {
            return new Audio(cached, MP3_TYPE);
        }
        try {
            byte[] mp3 = encode(wav);
            synchronized (CACHE) {
                CACHE.put(key, mp3);
            }
            return new Audio(mp3, MP3_TYPE);
        } catch (RuntimeException e) {
            return new Audio(wav, WAV_TYPE);
        }
    }

    static byte[] encode(byte[] wav) {
        WavUtils.WavInfo info = WavUtils.parse(wav);
        if (info.bitsPerSample() != 16) {
            throw new IllegalArgumentException("仅支持 16 位 PCM");
        }
        AudioFormat source = new AudioFormat(info.sampleRate(), 16, info.channels(), true, false);
        LameEncoder encoder = new LameEncoder(source, BIT_RATE, LameEncoder.CHANNEL_MODE_MONO,
                LameEncoder.QUALITY_MIDDLE, false);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(info.dataLength() / 8);
            byte[] buffer = new byte[encoder.getMP3BufferSize()];
            int chunk = encoder.getPCMBufferSize();
            int end = info.dataOffset() + info.dataLength();
            for (int pos = info.dataOffset(); pos < end; pos += chunk) {
                int n = encoder.encodeBuffer(wav, pos, Math.min(chunk, end - pos), buffer);
                out.write(buffer, 0, n);
            }
            int n = encoder.encodeFinish(buffer);
            out.write(buffer, 0, n);
            return out.toByteArray();
        } finally {
            encoder.close();
        }
    }
}
