package system.assessment.defense.application.manage;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import system.assessment.defense.application.dto.WebSocketResponse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @USER taoHouChao
 * @DATE 08:48 2025/8/15
 */
@Slf4j
public class WebsocketManager {

    private static final Map<Integer, WebSocketSession> TEXT_SESSIONS = new ConcurrentHashMap<>();

    private static final Map<Integer, WebSocketSession> VOICE_SESSIONS = new ConcurrentHashMap<>();

    private static final Map<Integer, WebSocketSession> VOICE_PLAY_SESSIONS = new ConcurrentHashMap<>();

    // 存储每个学生语音发送的中断标志
    private static final Map<Integer, AtomicBoolean> INTERRUPTION_FLAGS = new ConcurrentHashMap<>();

    private static final int AUDIO_SAMPLE_RATE = 24000;

    private static final int AUDIO_CHANNELS = 1;

    private static final int AUDIO_BITS_PER_SAMPLE = 16;

    public static void addTextSession(Integer userId, WebSocketSession session) {
        TEXT_SESSIONS.put(userId, session);
    }

    public static void addVoiceSession(Integer userId, WebSocketSession session) {
        VOICE_SESSIONS.put(userId, session);
    }

    public static void addVoicePlaySession(Integer userId, WebSocketSession session) {
        VOICE_PLAY_SESSIONS.put(userId, session);
    }
    public static void removeTextSession(Integer userId) {
        WebSocketSession textSession = TEXT_SESSIONS.remove(userId);
        if (textSession != null) {
            try {
                textSession.close();
            } catch (IOException e) {
                log.error("关闭session异常", e);
            }
        }
    }

    public static void removeVoiceSession(Integer userId) {
        WebSocketSession voiceSession = VOICE_SESSIONS.remove(userId);
        if (voiceSession != null) {
            try {
                voiceSession.close();
            } catch (IOException e) {
                log.error("关闭session异常", e);
            }
        }
    }

    public static void removeVoicePayload(Integer userId) {
        WebSocketSession voicePlaySession = VOICE_PLAY_SESSIONS.remove(userId);
        if (voicePlaySession != null) {
            try {
                voicePlaySession.close();
            } catch (IOException e) {
                log.error("关闭session异常", e);
            }
        }
    }

    public static WebSocketSession getTextSession(Integer userId) {
        return TEXT_SESSIONS.get(userId);
    }

    public static WebSocketSession getVoiceSession(Integer userId) {
        return VOICE_SESSIONS.get(userId);
    }

    public static WebSocketSession getVoicePlaySession(Integer userId) {
        return VOICE_PLAY_SESSIONS.get(userId);
    }

    public static void sendTextMessage(Integer studentId, WebSocketResponse response) {
        WebSocketSession session = TEXT_SESSIONS.get(studentId);
        if (session == null || !session.isOpen()) {
            log.warn("sendTextMessage: text session not found or closed for studentId:{}", studentId);
            return;
        }
        try {
            session.sendMessage(new TextMessage(JSONUtil.toJsonStr(response)));
        } catch (Exception e) {
            log.error("发送文本消息失败: studentId:{}, error:{}", studentId, e.getMessage(), e);
        }
    }

    public static void sendVoiceMessage(byte[] voice, Integer studentId){
        long start = System.currentTimeMillis();
        log.info("start send voice message, time:{}, voiceLength:{}", start, voice.length);
        startVoiceSending(studentId);
        try {
            log.info("sendVoiceMessage: {}", studentId);
            int chunkSize = 8 * 1024; // 8KB 每块（可调）
            // 分块发送二进制数据
            for (int i = 0; i < voice.length; i += chunkSize) {
                if (isVoiceSendingInterrupted(studentId)) {
                    log.info("Voice sending interrupted for studentId: {}", studentId);
                    break;
                }
                int len = Math.min(chunkSize, voice.length - i);
                byte[] chunk = Arrays.copyOfRange(voice, i, i + len);
                if (!sendVoiceChunk(chunk, studentId)) {
                    break;
                }
            }

            // 发送结束标记
            sendVoiceEnd(studentId);

            log.info("end send voice message, total time: {}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("发送语音消息失败: {}", e.getMessage(), e);
        }finally {
            // 清理中断标志
            finishVoiceSending(studentId);
        }
    }

    public static void startVoiceSending(Integer studentId) {
        INTERRUPTION_FLAGS.put(studentId, new AtomicBoolean(false));
    }

    public static void finishVoiceSending(Integer studentId) {
        INTERRUPTION_FLAGS.remove(studentId);
    }

    public static boolean isVoiceSendingInterrupted(Integer studentId) {
        AtomicBoolean flag = INTERRUPTION_FLAGS.get(studentId);
        return flag != null && flag.get();
    }

    public static boolean sendVoiceChunk(byte[] voice, Integer studentId) {
        if (voice == null || voice.length == 0) {
            return true;
        }
        WebSocketSession session = getVoicePlaySession(studentId);
        if (session == null || !session.isOpen()) {
            log.warn("voice play session is closed, studentId:{}", studentId);
            return false;
        }
        if (isVoiceSendingInterrupted(studentId)) {
            return false;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(Base64.getEncoder().encodeToString(addWavHeader(voice))));
            }
            return true;
        } catch (Exception e) {
            log.error("发送语音分片失败: studentId:{}, error:{}", studentId, e.getMessage(), e);
            return false;
        }
    }

    public static void sendVoiceEnd(Integer studentId) {
        WebSocketSession session = getVoicePlaySession(studentId);
        if (session == null || !session.isOpen()) {
            log.warn("voice play session is closed, skip end marker, studentId:{}", studentId);
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(JSONUtil.toJsonStr(WebSocketResponse.ofLast("audio:end"))));
            }
        } catch (Exception e) {
            log.error("发送语音结束标记失败: studentId:{}, error:{}", studentId, e.getMessage(), e);
        }
    }

    /**
     * 外部调用此方法来中断指定学生的语音发送
     */
    public static void interruptVoiceSending(Integer studentId) {
        AtomicBoolean flag = INTERRUPTION_FLAGS.get(studentId);
        if (flag != null) {
            flag.set(true);
            log.info("Interrupt signal sent for studentId: {}", studentId);
        } else {
            log.warn("No sending task found for studentId: {}", studentId);
        }
    }

    private static byte[] addWavHeader(byte[] pcmData) {
        ByteBuffer buffer = ByteBuffer.allocate(44 + pcmData.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        int blockAlign = AUDIO_CHANNELS * AUDIO_BITS_PER_SAMPLE / 8;
        int byteRate = AUDIO_SAMPLE_RATE * blockAlign;

        // WAV头（24kHz, 16bit, 单声道）
        buffer.put("RIFF".getBytes());
        buffer.putInt(36 + pcmData.length);
        buffer.put("WAVE".getBytes());
        buffer.put("fmt ".getBytes());
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) AUDIO_CHANNELS);
        buffer.putInt(AUDIO_SAMPLE_RATE);
        buffer.putInt(byteRate);
        buffer.putShort((short) blockAlign);
        buffer.putShort((short) AUDIO_BITS_PER_SAMPLE);
        buffer.put("data".getBytes());
        buffer.putInt(pcmData.length);
        buffer.put(pcmData);

        return buffer.array();
    }
}
