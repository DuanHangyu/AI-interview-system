package system.assessment.defense.application.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import system.assessment.defense.application.dto.OmniAudioResponseDTO;
import system.assessment.defense.infrastructure.repository.dao.mapper.TokenConsumerMapper;
import system.assessment.defense.infrastructure.repository.dao.po.TokenConsumerPO;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiManager {

    private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 10, 1000, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1000));

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TokenConsumerMapper tokenConsumerMapper;

    @Value("${dashscope.base-url}")
    private String baseUrl;

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.models.text}")
    private String textModel;

    @Value("${dashscope.models.omni}")
    private String omniModel;

    @Value("${dashscope.models.doc:qwen-long}")
    private String docModel;

    public String getTextModel() {
        return textModel;
    }

    public String getOmniModel() {
        return omniModel;
    }

    /**
     * 纯文本对话
     */
    public String chatCompletion(String model, String systemPrompt, String userPrompt) {
        return chatCompletion(model, systemPrompt, userPrompt, null, null, null);
    }

    /**
     * 带 JSON Schema 约束的对话
     */
    public String chatCompletionWithSchema(String model, String systemPrompt, String userPrompt,
                                            String schemaName, String schemaJson) {
        return chatCompletion(model, systemPrompt, userPrompt, schemaName, schemaJson, null);
    }

    /**
     * 带文件（PDF 等）的对话
     */
    public String chatCompletionWithFiles(String model, String systemPrompt, String userPrompt,
                                           List<FileInput> files) {
        return chatCompletion(model, systemPrompt, userPrompt, null, null, files);
    }

    /**
     * 上传文件到 DashScope（用于 qwen-long 文档理解）
     */
    public String uploadFile(byte[] data, String filename, String purpose) {
        try {
            MultipartBody multipartBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("purpose", purpose)
                    .addFormDataPart("file", filename,
                            RequestBody.create(data, MediaType.parse("application/octet-stream")))
                    .build();

            Request request = new Request.Builder()
                    .url(baseUrl + "/files")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(multipartBody)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error("upload file failed, status:{}, body:{}", response.code(), responseBody);
                    throw new RuntimeException("File upload failed: " + response.code() + " - " + responseBody);
                }
                JsonNode root = objectMapper.readTree(responseBody);
                String fileId = root.path("id").asText("");
                log.info("upload file success, fileId:{}, response:{}", fileId, responseBody);
                return fileId;
            }
        } catch (IOException e) {
            log.error("upload file error", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 使用 qwen-long 模型处理文档（PDF 提取等）
     */
    public String chatCompletionWithDocument(String systemPrompt, String fileId, String userPrompt,
                                              String schemaName, String schemaJson) {
        log.info("chatCompletionWithDocument fileId:{}, systemPrompt length:{}", fileId, systemPrompt != null ? systemPrompt.length() : 0);
        String rolePrompt = (systemPrompt != null && !systemPrompt.isBlank()) ? systemPrompt : "You are a helpful assistant.";
        return doCall(docModel, List.of(
                Map.of("role", "system", "content", rolePrompt),
                Map.of("role", "system", "content", "fileid://" + fileId),
                Map.of("role", "user", "content", userPrompt)
        ), schemaName, schemaJson, "文档处理");
    }

    /**
     * 带音频的对话（用于语音转写，Qwen-Omni 要求流式输出）
     */
    public String chatCompletionWithAudio(String model, String systemPrompt, byte[] audioBytes, String audioFormat, String userPrompt) {
        String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

        Map<String, Object> audioContent = Map.of(
                "type", "input_audio",
                "input_audio", Map.of(
                        "data", "data:;base64," + base64Audio,
                        "format", audioFormat
                )
        );

        Map<String, Object> textContent = Map.of(
                "type", "text",
                "text", userPrompt != null ? userPrompt : "请获取语音的转写内容"
        );

        List<Map<String, Object>> contentParts = new ArrayList<>();
        contentParts.add(audioContent);
        contentParts.add(textContent);

        Map<String, Object> message = Map.of(
                "role", "user",
                "content", contentParts
        );

        List<Map<String, Object>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content",
                    List.of(Map.of("type", "text", "text", systemPrompt))));
        }
        messages.add(message);

        return doStreamingCall(model, messages, "语音转写");
    }

    public OmniAudioResponseDTO chatCompletionWithAudioOutput(String model, String systemPrompt, String userPrompt,
                                                              String voice, String format) {
        return chatCompletionWithAudioOutput(model, systemPrompt, userPrompt, voice, format, null);
    }

    public OmniAudioResponseDTO chatCompletionWithAudioOutput(String model, String systemPrompt, String userPrompt,
                                                              String voice, String format,
                                                              Consumer<byte[]> audioChunkConsumer) {
        Map<String, Object> message = Map.of("role", "user", "content", userPrompt);
        List<Map<String, Object>> messages = buildMessages(systemPrompt, message);
        return doAudioStreamingCall(model, messages, voice, format, "Omni语音生成", audioChunkConsumer);
    }

    /**
     * 统一调用方法
     */
    private String chatCompletion(String model, String systemPrompt, String userPrompt,
                                   String schemaName, String schemaJson, List<FileInput> files) {
        if (files != null && !files.isEmpty()) {
            boolean hasPdf = files.stream().anyMatch(f -> f.mimeType().contains("pdf"));
            if (hasPdf) {
                FileInput pdfFile = files.stream()
                        .filter(f -> f.mimeType().contains("pdf"))
                        .findFirst()
                        .orElseThrow();
                String fileId = uploadFile(pdfFile.data(), "document.pdf", "file-extract");
                return chatCompletionWithDocument(systemPrompt, fileId, userPrompt, schemaName, schemaJson);
            }
        }

        List<Map<String, Object>> contentParts = new ArrayList<>();
        contentParts.add(Map.of("type", "text", "text", userPrompt));

        if (files != null && !files.isEmpty()) {
            for (FileInput file : files) {
                String base64Data = Base64.getEncoder().encodeToString(file.data());
                String mimeType = file.mimeType();
                contentParts.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url",
                                "data:" + mimeType + ";base64," + base64Data)
                ));
            }
        }

        Map<String, Object> message = Map.of("role", "user", "content", contentParts);
        List<Map<String, Object>> messages = buildMessages(systemPrompt, message);
        return doCall(model, messages, schemaName, schemaJson, null);
    }

    /**
     * 多轮对话（用于 GeminiController.lastQuestion 替换）
     */
    public String chatCompletionMultiTurn(String model, List<Map<String, Object>> messages) {
        return doCall(model, messages, null, null, null);
    }

    private List<Map<String, Object>> buildMessages(String systemPrompt, Map<String, Object> userMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(userMessage);
        return messages;
    }

    private String doCall(String model, List<Map<String, Object>> messages,
                           String schemaName, String schemaJson, String logLabel) {
        long start = System.currentTimeMillis();
        String uuid = UUID.randomUUID().toString();
        String label = logLabel != null ? logLabel : "chatCompletion";
        log.info("start {} request, uuid:{}, model:{}, time:{}", label, uuid, model, start);

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);

            if (schemaName != null && schemaJson != null) {
                body.put("response_format", Map.of("type", "json_object"));
            }

            String jsonBody = objectMapper.writeValueAsString(body);
            log.info("{} uuid:{}, model:{}, body length:{}", label, uuid, model, jsonBody.length());

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error("{} uuid:{} failed, status:{}, body:{}", label, uuid, response.code(), responseBody);
                    throw new RuntimeException("API call failed: " + response.code() + " - " + responseBody);
                }

                JsonNode root = objectMapper.readTree(responseBody);
                String content = root.path("choices").path(0).path("message").path("content").asText("");

                JsonNode usage = root.path("usage");
                int promptTokens = usage.path("prompt_tokens").asInt(0);
                int completionTokens = usage.path("completion_tokens").asInt(0);
                int totalTokens = usage.path("total_tokens").asInt(0);

                log.info("{} uuid:{}, prompt_tokens:{}, completion_tokens:{}, total_tokens:{}, time:{}",
                        label, uuid, promptTokens, completionTokens, totalTokens,
                        System.currentTimeMillis() - start);
                log.info("{} uuid:{}, response length:{}, time:{}", label, uuid,
                        content.length(), System.currentTimeMillis() - start);

                executor.execute(() -> {
                    try {
                        tokenConsumerMapper.insert(TokenConsumerPO.builder()
                                .method(label)
                                .param(jsonBody.length() > 2000 ? jsonBody.substring(0, 2000) : jsonBody)
                                .result(content.length() > 4000 ? content.substring(0, 4000) : content)
                                .promptTokenCount(promptTokens)
                                .candidatesTokenCount(completionTokens)
                                .totalTokenCount(totalTokens)
                                .cachedContentTokenCount(0)
                                .costTime(System.currentTimeMillis() - start)
                                .build());
                    } catch (Exception e) {
                        log.error("token log error", e);
                    }
                });

                return content;
            }
        } catch (IOException e) {
            log.error("{} uuid:{} error", label, uuid, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 流式调用（Qwen-Omni 要求 stream:true）
     */
    private String doStreamingCall(String model, List<Map<String, Object>> messages, String logLabel) {
        long start = System.currentTimeMillis();
        String uuid = UUID.randomUUID().toString();
        String label = logLabel != null ? logLabel : "streamingChat";
        log.info("start {} request, uuid:{}, model:{}, time:{}", label, uuid, model, start);

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", true);
            body.put("stream_options", Map.of("include_usage", true));
            body.put("modalities", List.of("text"));

            String jsonBody = objectMapper.writeValueAsString(body);
            log.info("{} uuid:{}, model:{}, body length:{}", label, uuid, model, jsonBody.length());

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .build();

            StringBuilder contentBuilder = new StringBuilder();
            int[] tokens = {0, 0, 0};

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    log.error("{} uuid:{} failed, status:{}, body:{}", label, uuid, response.code(), errorBody);
                    throw new RuntimeException("API call failed: " + response.code() + " - " + errorBody);
                }

                String line;
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(response.body().byteStream(), java.nio.charset.StandardCharsets.UTF_8));
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        JsonNode chunk = objectMapper.readTree(data);
                        JsonNode delta = chunk.path("choices").path(0).path("delta");
                        String text = delta.path("content").asText("");
                        if (!text.isEmpty()) {
                            contentBuilder.append(text);
                        }

                        JsonNode usage = chunk.path("usage");
                        if (!usage.isMissingNode()) {
                            tokens[0] = usage.path("prompt_tokens").asInt(0);
                            tokens[1] = usage.path("completion_tokens").asInt(0);
                            tokens[2] = usage.path("total_tokens").asInt(0);
                        }
                    }
                }
            }

            String content = contentBuilder.toString();
            log.info("{} uuid:{}, prompt_tokens:{}, completion_tokens:{}, total_tokens:{}, time:{}",
                    label, uuid, tokens[0], tokens[1], tokens[2],
                    System.currentTimeMillis() - start);

            final int fPromptTokens = tokens[0];
            final int fCompletionTokens = tokens[1];
            final int fTotalTokens = tokens[2];
            executor.execute(() -> {
                try {
                    tokenConsumerMapper.insert(TokenConsumerPO.builder()
                            .method(label)
                            .param(jsonBody.length() > 2000 ? jsonBody.substring(0, 2000) : jsonBody)
                            .result(content.length() > 4000 ? content.substring(0, 4000) : content)
                            .promptTokenCount(fPromptTokens)
                            .candidatesTokenCount(fCompletionTokens)
                            .totalTokenCount(fTotalTokens)
                            .cachedContentTokenCount(0)
                            .costTime(System.currentTimeMillis() - start)
                            .build());
                } catch (Exception e) {
                    log.error("token log error", e);
                }
            });

            return content;
        } catch (IOException e) {
            log.error("{} uuid:{} error", label, uuid, e);
            throw new RuntimeException(e);
        }
    }

    private OmniAudioResponseDTO doAudioStreamingCall(String model, List<Map<String, Object>> messages,
                                                      String voice, String format, String logLabel) {
        return doAudioStreamingCall(model, messages, voice, format, logLabel, null);
    }

    private OmniAudioResponseDTO doAudioStreamingCall(String model, List<Map<String, Object>> messages,
                                                      String voice, String format, String logLabel,
                                                      Consumer<byte[]> audioChunkConsumer) {
        long start = System.currentTimeMillis();
        String uuid = UUID.randomUUID().toString();
        String label = logLabel != null ? logLabel : "audioStreamingChat";
        log.info("start {} request, uuid:{}, model:{}, time:{}", label, uuid, model, start);

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", true);
            body.put("stream_options", Map.of("include_usage", true));
            body.put("modalities", List.of("text", "audio"));
            body.put("audio", Map.of("voice", voice, "format", format));
            body.put("enable_thinking", false);

            String jsonBody = objectMapper.writeValueAsString(body);
            log.info("{} uuid:{}, model:{}, body length:{}", label, uuid, model, jsonBody.length());

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    log.error("{} uuid:{} failed, status:{}, body:{}", label, uuid, response.code(), errorBody);
                    throw new RuntimeException("API call failed: " + response.code() + " - " + errorBody);
                }

                StringBuilder textBuilder = new StringBuilder();
                StringBuilder audioBuilder = new StringBuilder();
                StreamingBase64AudioDecoder audioDecoder = audioChunkConsumer != null
                        ? new StreamingBase64AudioDecoder(audioChunkConsumer)
                        : null;
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(response.body().byteStream(), java.nio.charset.StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    parseOmniAudioStreamingLine(line, objectMapper, textBuilder, audioBuilder, audioDecoder);
                }
                if (audioDecoder != null) {
                    audioDecoder.flush();
                }

                OmniAudioResponseDTO result = new OmniAudioResponseDTO();
                result.setText(textBuilder.toString());
                result.setAudioBase64(audioBuilder.toString());

                log.info("{} uuid:{}, text length:{}, audio bytes:{}, time:{}",
                        label, uuid, result.getText().length(), result.getAudioBytes().length,
                        System.currentTimeMillis() - start);

                executor.execute(() -> {
                    try {
                        tokenConsumerMapper.insert(TokenConsumerPO.builder()
                                .method(label)
                                .param(jsonBody.length() > 2000 ? jsonBody.substring(0, 2000) : jsonBody)
                                .result(result.getText().length() > 4000 ? result.getText().substring(0, 4000) : result.getText())
                                .promptTokenCount(0)
                                .candidatesTokenCount(0)
                                .totalTokenCount(0)
                                .cachedContentTokenCount(0)
                                .costTime(System.currentTimeMillis() - start)
                                .build());
                    } catch (Exception e) {
                        log.error("token log error", e);
                    }
                });
                return result;
            }
        } catch (IOException e) {
            log.error("{} uuid:{} error", label, uuid, e);
            throw new RuntimeException(e);
        }
    }

    static OmniAudioResponseDTO parseOmniAudioStreamingLines(List<String> lines) {
        StringBuilder textBuilder = new StringBuilder();
        StringBuilder audioBuilder = new StringBuilder();
        ObjectMapper mapper = new ObjectMapper();

        for (String line : lines) {
            parseOmniAudioStreamingLine(line, mapper, textBuilder, audioBuilder, null);
        }

        OmniAudioResponseDTO response = new OmniAudioResponseDTO();
        response.setText(textBuilder.toString());
        response.setAudioBase64(audioBuilder.toString());
        return response;
    }

    private static void parseOmniAudioStreamingLine(String line, ObjectMapper mapper,
                                                    StringBuilder textBuilder,
                                                    StringBuilder audioBuilder,
                                                    StreamingBase64AudioDecoder audioDecoder) {
        if (line == null || !line.startsWith("data: ")) {
            return;
        }
        String data = line.substring(6).trim();
        if (data.isBlank() || "[DONE]".equals(data)) {
            return;
        }
        try {
            JsonNode chunk = mapper.readTree(data);
            JsonNode delta = chunk.path("choices").path(0).path("delta");
            String text = delta.path("content").asText("");
            if (!text.isEmpty()) {
                textBuilder.append(text);
            }
            String audio = delta.path("audio").path("data").asText("");
            if (!audio.isEmpty()) {
                audioBuilder.append(audio);
                if (audioDecoder != null) {
                    audioDecoder.accept(audio);
                }
            }
        } catch (Exception e) {
            log.warn("skip malformed omni streaming line: {}", line, e);
        }
    }

    private static class StreamingBase64AudioDecoder {

        private final Consumer<byte[]> audioChunkConsumer;

        private final StringBuilder pending = new StringBuilder();

        StreamingBase64AudioDecoder(Consumer<byte[]> audioChunkConsumer) {
            this.audioChunkConsumer = audioChunkConsumer;
        }

        void accept(String base64Chunk) {
            if (base64Chunk == null || base64Chunk.isBlank()) {
                return;
            }
            pending.append(base64Chunk.replaceAll("\\s+", ""));
            int paddingIndex = pending.indexOf("=");
            int decodeLength = paddingIndex >= 0
                    ? pending.length()
                    : pending.length() - pending.length() % 4;
            decodeAvailableBytes(decodeLength);
        }

        void flush() {
            int decodeLength = pending.length();
            int remainder = decodeLength % 4;
            if (remainder != 0) {
                pending.append("=".repeat(4 - remainder));
                decodeLength = pending.length();
            }
            decodeAvailableBytes(decodeLength);
        }

        private void decodeAvailableBytes(int decodeLength) {
            if (decodeLength <= 0) {
                return;
            }
            String chunk = pending.substring(0, decodeLength);
            byte[] decodedBytes = Base64.getDecoder().decode(chunk);
            if (decodedBytes.length > 0) {
                audioChunkConsumer.accept(decodedBytes);
            }
            pending.delete(0, decodeLength);
        }
    }

    /**
     * 文件输入 DTO
     */
    public record FileInput(byte[] data, String mimeType) {}
}
