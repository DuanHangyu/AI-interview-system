package system.assessment.defense.application.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import system.assessment.defense.infrastructure.repository.dao.mapper.TokenConsumerMapper;
import system.assessment.defense.infrastructure.repository.dao.po.TokenConsumerPO;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiManager {

    // token 记录只是统计用途，队列满时直接丢弃，绝不能反压业务线程
    private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 2, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2000), new ThreadPoolExecutor.DiscardPolicy());

    private static final int MAX_ATTEMPTS = 3;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TokenConsumerMapper tokenConsumerMapper;
    private final AiRateLimiter rateLimiter;

    @Value("${dashscope.base-url}")
    private String baseUrl;

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.models.text}")
    private String textModel;

    @Value("${dashscope.models.doc:qwen-long}")
    private String docModel;

    @Value("${dashscope.enable-thinking:false}")
    private boolean enableThinking;

    public String getTextModel() {
        return textModel;
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

    private List<Map<String, Object>> buildMessages(String systemPrompt, Map<String, Object> userMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(userMessage);
        return messages;
    }

    private void applyThinkingMode(Map<String, Object> body) {
        body.put("enable_thinking", enableThinking);
    }

    private String doCall(String model, List<Map<String, Object>> messages,
                           String schemaName, String schemaJson, String logLabel) {
        return rateLimiter.call(AiRateLimiter.Capability.LLM, () -> {
            for (int attempt = 1; ; attempt++) {
                try {
                    return doCallOnce(model, messages, schemaName, schemaJson, logLabel);
                } catch (AiCallException e) {
                    if (!e.isRetryable() || attempt >= MAX_ATTEMPTS) {
                        throw e;
                    }
                    AiCallException.backoff(attempt);
                }
            }
        });
    }

    private String doCallOnce(String model, List<Map<String, Object>> messages,
                              String schemaName, String schemaJson, String logLabel) {
        long start = System.currentTimeMillis();
        String uuid = UUID.randomUUID().toString();
        String label = logLabel != null ? logLabel : "chatCompletion";
        log.info("start {} request, uuid:{}, model:{}, time:{}", label, uuid, model, start);

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            applyThinkingMode(body);

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
                    throw AiCallException.ofStatus(response.code(), responseBody);
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
            throw AiCallException.ofIo(e);
        }
    }

    /**
     * 文件输入 DTO
     */
    public record FileInput(byte[] data, String mimeType) {}
}
