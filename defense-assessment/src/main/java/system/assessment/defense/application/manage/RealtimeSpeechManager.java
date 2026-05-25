package system.assessment.defense.application.manage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimeSpeechManager {

    private static final String INTERVIEW_TRANSCRIPTION_INSTRUCTIONS = """
            你是 AI 面试系统的实时语音转写助手。
            只转写学生正在回答面试题或答辩内容的语音，保持原意，不总结、不补充、不回答问题。
            如果听不清，请尽量转写可确认的内容。
            """;

    private final okhttp3.OkHttpClient okHttpClient;
    private final RealtimeSpeechEventFactory eventFactory;

    private final Map<Integer, RealtimeSpeechSession> sessions = new ConcurrentHashMap<>();

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.realtime.enabled:true}")
    private boolean enabled;

    @Value("${dashscope.realtime.url:wss://dashscope.aliyuncs.com/api-ws/v1/realtime}")
    private String realtimeUrl;

    @Value("${dashscope.realtime.model:qwen3.5-omni-plus-realtime}")
    private String realtimeModel;

    @Value("${dashscope.realtime.voice:Ethan}")
    private String realtimeVoice;

    @Value("${dashscope.realtime.transcript-wait-ms:5000}")
    private long transcriptWaitMs;

    public void open(Integer studentId, WebSocketSession clientSession) {
        if (!enabled) {
            log.info("Realtime speech disabled, studentId:{}", studentId);
            return;
        }
        if (StringUtils.isBlank(apiKey)) {
            log.warn("Realtime speech skipped because dashscope api key is blank, studentId:{}", studentId);
            return;
        }

        close(studentId);
        RealtimeSpeechSession session = new RealtimeSpeechSession(studentId, clientSession);
        sessions.put(studentId, session);

        Request request = new Request.Builder()
                .url(realtimeUrl + "?model=" + realtimeModel)
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();
        okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                session.upstream = webSocket;
                session.open.set(true);
                webSocket.send(eventFactory.sessionUpdate(realtimeVoice, INTERVIEW_TRANSCRIPTION_INSTRUCTIONS));
                log.info("Realtime speech connected, studentId:{}, model:{}", studentId, realtimeModel);
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                handleServerEvent(session, text);
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                session.open.set(false);
                log.info("Realtime speech closed, studentId:{}, code:{}, reason:{}", studentId, code, reason);
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
                session.open.set(false);
                session.completeExceptionally(t);
                String responseMessage = response != null ? response.message() : "";
                log.error("Realtime speech failed, studentId:{}, response:{}", studentId, responseMessage, t);
            }
        });
    }

    public void startTurn(Integer studentId) {
        RealtimeSpeechSession session = sessions.get(studentId);
        if (session == null) {
            return;
        }
        session.startTurn();
        if (session.isOpen()) {
            session.send(eventFactory.inputAudioClear());
        }
    }

    public boolean appendAudio(Integer studentId, ByteBuffer payload) {
        RealtimeSpeechSession session = sessions.get(studentId);
        if (session == null || !session.isOpen()) {
            return false;
        }
        byte[] bytes = copyRemaining(payload);
        if (bytes.length == 0) {
            return true;
        }
        return session.send(eventFactory.inputAudioAppend(bytes));
    }

    public Optional<String> completeTurnAndAwaitTranscript(Integer studentId) {
        RealtimeSpeechSession session = sessions.get(studentId);
        if (session == null || !session.isOpen()) {
            return Optional.empty();
        }
        session.send(eventFactory.inputAudioCommit());
        try {
            String transcript = session.transcriptFuture.get(transcriptWaitMs, TimeUnit.MILLISECONDS);
            return StringUtils.isBlank(transcript) ? Optional.empty() : Optional.of(transcript.trim());
        } catch (TimeoutException e) {
            log.warn("Realtime transcript timeout, studentId:{}, waitMs:{}", studentId, transcriptWaitMs);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Realtime transcript unavailable, studentId:{}, reason:{}", studentId, e.getMessage());
            return Optional.empty();
        }
    }

    public void close(Integer studentId) {
        RealtimeSpeechSession session = sessions.remove(studentId);
        if (session != null) {
            session.close();
        }
    }

    public Duration transcriptWaitDuration() {
        return Duration.ofMillis(transcriptWaitMs);
    }

    private void handleServerEvent(RealtimeSpeechSession session, String text) {
        eventFactory.extractCompletedTranscript(text).ifPresent(transcript -> {
            session.complete(transcript);
            sendClientTranscript(session.clientSession, transcript);
        });
    }

    private void sendClientTranscript(WebSocketSession clientSession, String transcript) {
        if (clientSession == null || !clientSession.isOpen()) {
            return;
        }
        try {
            synchronized (clientSession) {
                clientSession.sendMessage(new TextMessage(transcript));
            }
        } catch (IOException e) {
            log.warn("send realtime transcript to client failed:{}", e.getMessage());
        }
    }

    private byte[] copyRemaining(ByteBuffer buffer) {
        ByteBuffer copy = buffer.asReadOnlyBuffer();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    private static class RealtimeSpeechSession {
        private final Integer studentId;
        private final WebSocketSession clientSession;
        private final AtomicBoolean open = new AtomicBoolean(false);
        private volatile WebSocket upstream;
        private volatile CompletableFuture<String> transcriptFuture = new CompletableFuture<>();

        private RealtimeSpeechSession(Integer studentId, WebSocketSession clientSession) {
            this.studentId = studentId;
            this.clientSession = clientSession;
        }

        private boolean isOpen() {
            return open.get() && upstream != null;
        }

        private synchronized void startTurn() {
            transcriptFuture = new CompletableFuture<>();
        }

        private boolean send(String eventJson) {
            WebSocket webSocket = upstream;
            return webSocket != null && webSocket.send(eventJson);
        }

        private void complete(String transcript) {
            transcriptFuture.complete(transcript);
        }

        private void completeExceptionally(Throwable t) {
            transcriptFuture.completeExceptionally(t);
        }

        private void close() {
            open.set(false);
            WebSocket webSocket = upstream;
            if (webSocket != null) {
                webSocket.close(1000, "client closed");
            }
            transcriptFuture.complete("");
        }

        @Override
        public String toString() {
            return "RealtimeSpeechSession{studentId=" + studentId + '}';
        }
    }
}
