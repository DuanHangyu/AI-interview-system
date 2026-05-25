package system.assessment.defense.application.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimeVoiceOutputManager {

    private static final String QUESTION_VOICE_INSTRUCTIONS = """
            你是面试现场的语音播报器。
            你的唯一任务是把题目文本原样朗读成语音。
            禁止回答题目，禁止解释题目，禁止扩写、改写或补充任何内容。
            语速自然，语气清晰、专业、平稳。
            """;

    private final okhttp3.OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final RealtimeSpeechEventFactory eventFactory;

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.realtime.voice-output-enabled:true}")
    private boolean enabled;

    @Value("${dashscope.realtime.url:wss://dashscope.aliyuncs.com/api-ws/v1/realtime}")
    private String realtimeUrl;

    @Value("${dashscope.realtime.model:qwen3.5-omni-plus-realtime}")
    private String realtimeModel;

    @Value("${dashscope.realtime.voice:Ethan}")
    private String realtimeVoice;

    @Value("${dashscope.realtime.voice-output-wait-ms:12000}")
    private long voiceOutputWaitMs;

    public boolean streamQuestionVoice(String questionText, Consumer<byte[]> audioChunkConsumer) {
        if (!enabled || StringUtils.isBlank(apiKey) || StringUtils.isBlank(questionText)) {
            return false;
        }

        CompletableFuture<Boolean> done = new CompletableFuture<>();
        AtomicBoolean hasAudio = new AtomicBoolean(false);
        Request request = new Request.Builder()
                .url(realtimeUrl + "?model=" + realtimeModel)
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                webSocket.send(eventFactory.audioOutputSessionUpdate(realtimeVoice, QUESTION_VOICE_INSTRUCTIONS));
                webSocket.send(eventFactory.responseCreateForAudio(buildReadQuestionPrompt(questionText)));
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                handleServerEvent(webSocket, text, audioChunkConsumer, hasAudio, done);
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                done.complete(hasAudio.get());
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
                log.warn("Realtime voice output failed, model:{}, error:{}", realtimeModel, t.getMessage());
                done.complete(hasAudio.get());
            }
        });

        try {
            return done.get(voiceOutputWaitMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Realtime voice output timeout or unavailable, model:{}, waitMs:{}, error:{}",
                    realtimeModel, voiceOutputWaitMs, e.getMessage());
            return hasAudio.get();
        }
    }

    private void handleServerEvent(WebSocket webSocket,
                                   String text,
                                   Consumer<byte[]> audioChunkConsumer,
                                   AtomicBoolean hasAudio,
                                   CompletableFuture<Boolean> done) {
        try {
            JsonNode root = objectMapper.readTree(text);
            String type = root.path("type").asText("");
            switch (type) {
                case "response.audio.delta" -> {
                    String delta = root.path("delta").asText("");
                    if (StringUtils.isNotBlank(delta)) {
                        hasAudio.set(true);
                        audioChunkConsumer.accept(Base64.getDecoder().decode(delta));
                    }
                }
                case "response.audio.done", "response.done" -> {
                    done.complete(hasAudio.get());
                    webSocket.close(1000, "voice done");
                }
                case "error" -> {
                    log.warn("Realtime voice output error event:{}", root.path("error"));
                    done.complete(hasAudio.get());
                    webSocket.close(1000, "voice error");
                }
                default -> {
                }
            }
        } catch (Exception e) {
            log.warn("Realtime voice output event parse failed:{}", e.getMessage());
        }
    }

    private String buildReadQuestionPrompt(String questionText) {
        return """
                请把下面的题目文本原样朗读成语音。
                输出内容只能是题目文本本身，禁止回答、解释、扩写或改写。

                题目文本：
                %s
                """.formatted(questionText);
    }
}
