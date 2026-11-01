package system.assessment.defense.application.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.houbb.opencc4j.util.ZhConverterUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import system.assessment.defense.infrastructure.common.WavUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 语音能力：题目播报（qwen3-tts-flash）与回答转写（qwen3-asr-flash）。
 * <p>
 * 两者都是普通的 HTTP 请求-响应，不再依赖与百炼之间的长连接（实时语音 WebSocket），
 * 由 {@link AiRateLimiter} 控制并发，网络错误 / 429 / 5xx 自动退避重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpeechManager {

    private static final MediaType JSON = MediaType.parse("application/json");
    private static final int MAX_ATTEMPTS = 3;
    private static final int TTS_MAX_CHARS = 500;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AiRateLimiter rateLimiter;

    @Value("${dashscope.base-url}")
    private String compatibleBaseUrl;

    @Value("${dashscope.native-base-url:https://dashscope.aliyuncs.com/api/v1}")
    private String nativeBaseUrl;

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.models.tts:qwen3-tts-flash}")
    private String ttsModel;

    @Value("${dashscope.models.asr:qwen3-asr-flash}")
    private String asrModel;

    @Value("${dashscope.tts.voice:Cherry}")
    private String ttsVoice;

    /**
     * qwen3-asr-flash 单次请求的音频时长上限是 3 分钟，留出余量按 2 分钟切片。
     */
    @Value("${dashscope.asr.chunk-seconds:120}")
    private int asrChunkSeconds;

    /**
     * 把题目文本合成为 WAV 语音。
     */
    public byte[] synthesize(String text) {
        String speech = toSpeakableText(text);
        if (StringUtils.isBlank(speech)) {
            throw AiCallException.of("题目文本为空，无法合成语音");
        }
        return rateLimiter.call(AiRateLimiter.Capability.TTS, () -> withRetry("tts", () -> {
            Map<String, Object> body = Map.of(
                    "model", ttsModel,
                    "input", Map.of("text", speech, "voice", ttsVoice, "language_type", "Chinese"));
            JsonNode root = post(httpClient, nativeBaseUrl + "/services/aigc/multimodal-generation/generation", body);
            String audioUrl = root.path("output").path("audio").path("url").asText("");
            if (StringUtils.isBlank(audioUrl)) {
                throw AiCallException.of("语音合成未返回音频: " + root);
            }
            return WavUtils.normalize(download(audioUrl));
        }));
    }

    /**
     * 识别一段 WAV 录音。超过单次时长上限的录音会被切片后依次识别再拼接。
     *
     * @param context 可选的上下文（例如题目原文），用于提升专有名词的识别准确率
     * @return 识别文本；没有识别到语音时返回空串
     */
    public String transcribe(byte[] wav, String context) {
        List<byte[]> chunks = WavUtils.split(wav, asrChunkSeconds);
        StringBuilder text = new StringBuilder();
        for (byte[] chunk : chunks) {
            if (WavUtils.parse(chunk).durationSeconds() < 0.3) {
                continue;
            }
            String part = rateLimiter.call(AiRateLimiter.Capability.ASR,
                    () -> withRetry("asr", () -> transcribeChunk(chunk, context)));
            text.append(part);
        }
        String result = text.toString().trim();
        return ZhConverterUtil.containsTraditional(result) ? ZhConverterUtil.toSimple(result) : result;
    }

    private String transcribeChunk(byte[] wav, String context) throws IOException {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (StringUtils.isNotBlank(context)) {
            messages.add(Map.of("role", "system",
                    "content", List.of(Map.of("text", StringUtils.abbreviate(context, 2000)))));
        }
        String dataUri = "data:audio/wav;base64," + Base64.getEncoder().encodeToString(wav);
        messages.add(Map.of("role", "user",
                "content", List.of(Map.of("type", "input_audio", "input_audio", Map.of("data", dataUri)))));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", asrModel);
        body.put("messages", messages);
        body.put("stream", false);
        body.put("asr_options", Map.of("enable_itn", true));
        JsonNode root = post(asrClient(), compatibleBaseUrl + "/chat/completions", body);
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    /**
     * 识别耗时与音频时长成正比，读超时比通用客户端更长；复用同一个连接池
     */
    private OkHttpClient asrClient() {
        OkHttpClient client = asrClient;
        if (client == null) {
            client = httpClient.newBuilder()
                    .readTimeout(Duration.ofSeconds(300))
                    .callTimeout(Duration.ofSeconds(330))
                    .build();
            asrClient = client;
        }
        return client;
    }

    private volatile OkHttpClient asrClient;

    private JsonNode post(OkHttpClient client, String url, Object body) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(objectMapper.writeValueAsBytes(body), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw AiCallException.ofStatus(response.code(), responseBody);
            }
            return objectMapper.readTree(responseBody);
        }
    }

    private byte[] download(String url) throws IOException {
        try (Response response = httpClient.newCall(new Request.Builder().url(url).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw AiCallException.ofStatus(response.code(), "download audio failed");
            }
            return response.body().bytes();
        }
    }

    private <T> T withRetry(String label, IoSupplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            long start = System.currentTimeMillis();
            try {
                T result = action.get();
                log.info("{} success, attempt:{}, cost:{}ms", label, attempt, System.currentTimeMillis() - start);
                return result;
            } catch (IOException e) {
                AiCallException wrapped = AiCallException.ofIo(e);
                if (attempt >= MAX_ATTEMPTS) {
                    throw wrapped;
                }
                log.warn("{} io error, attempt:{}, error:{}", label, attempt, e.getMessage());
            } catch (AiCallException e) {
                if (!e.isRetryable() || attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("{} failed, attempt:{}, error:{}", label, attempt, e.getMessage());
            }
            AiCallException.backoff(attempt);
        }
    }

    /**
     * 去掉 Markdown 标记和代码块，避免把符号读出来；超长题目只朗读前面部分（全文仍会显示在屏幕上）。
     */
    static String toSpeakableText(String text) {
        if (text == null) {
            return "";
        }
        String plain = text
                .replaceAll("(?s)```.*?```", "（代码见屏幕）")
                .replaceAll("`([^`]*)`", "$1")
                .replaceAll("!?\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^\\s{0,3}(#{1,6}|>|[-*+]|\\d+\\.)\\s+", "")
                .replaceAll("[*_~|]", "")
                .replaceAll("\\s+", " ")
                .trim();
        return StringUtils.abbreviate(plain, TTS_MAX_CHARS);
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}
