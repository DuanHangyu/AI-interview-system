package system.assessment.defense.infrastructure.common;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 最小化的 PCM WAV 解析 / 生成工具。
 */
public final class WavUtils {

    private static final int HEADER_SIZE = 44;

    private WavUtils() {
    }

    public record WavInfo(int sampleRate, int channels, int bitsPerSample, int dataOffset, int dataLength) {

        public int bytesPerSecond() {
            return sampleRate * channels * bitsPerSample / 8;
        }

        public double durationSeconds() {
            return bytesPerSecond() == 0 ? 0 : (double) dataLength / bytesPerSecond();
        }
    }

    /**
     * 解析 PCM WAV 头。流式生成的 WAV 头里 data 长度常常是占位值，这里以实际字节数为准。
     *
     * @throws IllegalArgumentException 不是合法的 PCM WAV
     */
    public static WavInfo parse(byte[] wav) {
        if (wav == null || wav.length < HEADER_SIZE
                || !"RIFF".equals(ascii(wav, 0)) || !"WAVE".equals(ascii(wav, 8))) {
            throw new IllegalArgumentException("不是有效的 WAV 文件");
        }
        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int pos = 12;
        Integer sampleRate = null, channels = null, bits = null;
        while (pos + 8 <= wav.length) {
            String id = ascii(wav, pos);
            int size = buffer.getInt(pos + 4);
            int body = pos + 8;
            if ("fmt ".equals(id)) {
                int format = buffer.getShort(body) & 0xffff;
                if (format != 1) {
                    throw new IllegalArgumentException("仅支持 PCM 编码的 WAV");
                }
                channels = buffer.getShort(body + 2) & 0xffff;
                sampleRate = buffer.getInt(body + 4);
                bits = buffer.getShort(body + 14) & 0xffff;
            } else if ("data".equals(id)) {
                if (sampleRate == null) {
                    throw new IllegalArgumentException("WAV 缺少 fmt 块");
                }
                int available = wav.length - body;
                int length = (size <= 0 || size > available) ? available : size;
                return new WavInfo(sampleRate, channels, bits, body, length);
            }
            if (size < 0 || body + (long) size > wav.length) {
                break;
            }
            pos = body + size + (size & 1);
        }
        throw new IllegalArgumentException("WAV 缺少 data 块");
    }

    /**
     * 重新生成一个头部长度字段正确的 WAV（部分浏览器对占位长度的 WAV 无法正确显示时长）。
     */
    public static byte[] normalize(byte[] wav) {
        WavInfo info = parse(wav);
        return build(wav, info.dataOffset(), info.dataLength(), info);
    }

    /**
     * 按最大时长切分成多个独立的 WAV，用于有单段时长上限的语音识别接口。
     */
    public static List<byte[]> split(byte[] wav, int maxSeconds) {
        WavInfo info = parse(wav);
        int blockAlign = Math.max(1, info.channels() * info.bitsPerSample() / 8);
        int chunkBytes = Math.max(blockAlign, info.bytesPerSecond() * maxSeconds / blockAlign * blockAlign);
        List<byte[]> chunks = new ArrayList<>();
        for (int offset = 0; offset < info.dataLength(); offset += chunkBytes) {
            int length = Math.min(chunkBytes, info.dataLength() - offset);
            chunks.add(build(wav, info.dataOffset() + offset, length, info));
        }
        return chunks;
    }

    /**
     * 录音的峰值电平（0～1）。只支持 16bit PCM，其它格式返回 1，即不做静音判断。
     */
    public static double peakLevel(byte[] wav) {
        WavInfo info = parse(wav);
        if (info.bitsPerSample() != 16) {
            return 1;
        }
        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int peak = 0;
        int end = info.dataOffset() + info.dataLength() - 1;
        for (int pos = info.dataOffset(); pos < end; pos += 2) {
            peak = Math.max(peak, Math.abs((int) buffer.getShort(pos)));
        }
        return peak / 32768.0;
    }

    private static byte[] build(byte[] source, int offset, int length, WavInfo info) {
        int blockAlign = info.channels() * info.bitsPerSample() / 8;
        ByteBuffer out = ByteBuffer.allocate(HEADER_SIZE + length).order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.putInt(36 + length);
        out.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        out.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        out.putInt(16);
        out.putShort((short) 1);
        out.putShort((short) info.channels());
        out.putInt(info.sampleRate());
        out.putInt(info.bytesPerSecond());
        out.putShort((short) blockAlign);
        out.putShort((short) info.bitsPerSample());
        out.put("data".getBytes(StandardCharsets.US_ASCII));
        out.putInt(length);
        out.put(source, offset, length);
        return out.array();
    }

    private static String ascii(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }
}
