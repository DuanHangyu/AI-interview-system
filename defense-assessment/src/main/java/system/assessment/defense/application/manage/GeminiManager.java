package system.assessment.defense.application.manage;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.houbb.opencc4j.util.ZhConverterUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import system.assessment.defense.application.dto.AssessmentEvaluationDTO;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiManager {

    private static final String VOICE_TRANSCRIPTION_SYSTEM_PROMPT = """
            你是一个专业的语音转写引擎。你的唯一任务是准确转录用户提供的音频中的语音内容。
            — 如果音频中包含清晰可辨的人声，请逐字转写，保留原始语句结构，不添加标点以外的任何文字。
            — 如果读取完音频的所有内容，没有识别到有效的内容，请仅输出："没有回答"。
            — 禁止添加解释、注释、前缀、后缀、标题、语气词或任何形式的旁白。
            — 输出必须干净、纯粹，仅包含转写文本或"没有回答"。
            - 如果是中文，则使用简体中文，禁止使用繁体中文
            """;

    private static final String PDF_EXTRACTION_SYSTEM_PROMPT = """
            你是一位专业的文档分析专家，擅长从上传的 PDF 文件中提取关键信息。

            你的任务包括：
            1. 只输出结果，不重复指令，不添加解释
            2. 如果问题无法从 PDF 中找到答案，请明确说明"未在文档中找到相关信息"。
            3. 使用中文回答，语言简洁、专业。
            """;

    private static final String DEFENSE_QUESTION_EVALUATION_SCHEMA = """
            {
              "type": "object",
              "required": ["analysis", "result", "defense", "summary"],
              "additionalProperties": false,
              "properties": {
                "result": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "required": ["id", "question", "answer", "followQuestion", "followAnswer"],
                    "additionalProperties": false,
                    "properties": {
                      "id": {"type": "integer", "description": "问题Id"},
                      "question": {"type": "string", "description": "问题"},
                      "answer": {"type": "string", "description": "回答"},
                      "followQuestion": {"type": "string", "description": "追问问题"},
                      "followAnswer": {"type": "string", "description": "追加问题回答"}
                    }
                  }
                },
                "defense": {
                  "type": "object",
                  "required": ["defenseAnswer"],
                  "additionalProperties": false,
                  "properties": {
                    "defenseAnswer": {"type": "string", "description": "答辩内容"}
                  }
                },
                "analysis": {
                  "type": "object",
                  "required": ["analysis", "suggestions", "strengths", "weaknesses"],
                  "additionalProperties": false,
                  "properties": {
                    "analysis": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "required": ["name", "description", "score"],
                        "additionalProperties": false,
                        "properties": {
                          "name": {"type": "string", "description": "考核项"},
                          "score": {"type": "number", "description": "考核项评分"},
                          "description": {"type": "string", "description": "考核项评价"}
                        }
                      }
                    },
                    "suggestions": {"type": "array", "items": {"type": "string"}},
                    "strengths": {"type": "array", "items": {"type": "string"}},
                    "weaknesses": {"type": "array", "items": {"type": "string"}}
                  }
                },
                "summary": {"type": "string", "description": "总结"}
              }
            }
            """;

    private static final String DEFENSE_ONLY_EVALUATION_SCHEMA = """
            {
              "type": "object",
              "required": ["analysis", "defense", "summary"],
              "additionalProperties": false,
              "properties": {
                "defense": {
                  "type": "object",
                  "required": ["defenseAnswer"],
                  "additionalProperties": false,
                  "properties": {
                    "defenseAnswer": {"type": "string", "description": "答辩内容"}
                  }
                },
                "analysis": {
                  "type": "object",
                  "required": ["analysis", "suggestions", "strengths", "weaknesses"],
                  "additionalProperties": false,
                  "properties": {
                    "analysis": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "required": ["name", "description", "score"],
                        "additionalProperties": false,
                        "properties": {
                          "name": {"type": "string", "description": "考核项"},
                          "score": {"type": "number", "description": "考核项评分"},
                          "description": {"type": "string", "description": "考核项评价"}
                        }
                      }
                    },
                    "suggestions": {"type": "array", "items": {"type": "string"}},
                    "strengths": {"type": "array", "items": {"type": "string"}},
                    "weaknesses": {"type": "array", "items": {"type": "string"}}
                  }
                },
                "summary": {"type": "string", "description": "总结"}
              }
            }
            """;

    private static final String QUESTION_ONLY_EVALUATION_SCHEMA = """
            {
              "type": "object",
              "required": ["analysis", "result", "summary"],
              "additionalProperties": false,
              "properties": {
                "result": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "required": ["id", "question", "answer", "followQuestion", "followAnswer"],
                    "additionalProperties": false,
                    "properties": {
                      "id": {"type": "integer", "description": "问题Id"},
                      "question": {"type": "string", "description": "问题"},
                      "answer": {"type": "string", "description": "回答"},
                      "followQuestion": {"type": "string", "description": "追问问题"},
                      "followAnswer": {"type": "string", "description": "追加问题回答"}
                    }
                  }
                },
                "analysis": {
                  "type": "object",
                  "required": ["analysis", "suggestions", "strengths", "weaknesses"],
                  "additionalProperties": false,
                  "properties": {
                    "analysis": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "required": ["name", "description", "score"],
                        "additionalProperties": false,
                        "properties": {
                          "name": {"type": "string", "description": "考核项"},
                          "score": {"type": "number", "description": "考核项评分"},
                          "description": {"type": "string", "description": "考核项评价"}
                        }
                      }
                    },
                    "suggestions": {"type": "array", "items": {"type": "string"}},
                    "strengths": {"type": "array", "items": {"type": "string"}},
                    "weaknesses": {"type": "array", "items": {"type": "string"}}
                  }
                },
                "summary": {"type": "string", "description": "总结"}
              }
            }
            """;

    private final OpenAiManager openAiManager;
    private final ObjectMapper objectMapper;

    private static final String LIVE_QUESTION_SYSTEM_PROMPT = """
            你是一名专业的大学考核教授。你需要根据学生的答辩内容和考核标准，现场生成针对性的考核问题。

            要求：
            1. 仔细阅读学生的答辩内容（口述转写文本）和已解析的考试材料上下文
            2. 根据考核标准识别所有考核维度
            3. 按维度均匀分配题目数量，确保每个维度都有涉及
            4. 题目必须针对学生答辩中的实际内容、论点和表述来提问
            5. 题目难度适中，适合大学水平
            6. 输出格式必须为标准 JSON

            JSON格式如下:
            {
              "questions": [
                {
                  "questionDimension": "维度名称",
                  "question": "具体问题"
                }
              ]
            }
            """;

    private static final String LIVE_QUESTION_SCHEMA = """
            {
              "type": "object",
              "required": ["questions"],
              "additionalProperties": false,
              "properties": {
                "questions": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "required": ["questionDimension", "question"],
                    "additionalProperties": false,
                    "properties": {
                      "questionDimension": { "type": "string" },
                      "question": { "type": "string" }
                    }
                  }
                }
              }
            }
            """;

    @Retryable(retryFor = RuntimeException.class)
    public String generateLiveQuestions(String defenseContent, String materialContext,
                                         String criteria, String requirements, int questionCount) {
        String uuid = java.util.UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        log.info("start generateLiveQuestions request, uuid:{}, time:{}", uuid, start);

        String userPrompt = """
                【考核标准】
                %s

                【考核要求】
                %s

                【考试材料上下文】
                %s

                【学生答辩内容】
                %s

                【题目数量】
                %d

                请根据以上信息，按考核维度均匀出题，共 %d 道题。
                """.formatted(criteria, requirements,
                StringUtils.defaultIfBlank(materialContext, "无"),
                StringUtils.defaultIfBlank(defenseContent, "无"),
                questionCount, questionCount);

        try {
            String text = openAiManager.chatCompletionWithSchema(
                    openAiManager.getTextModel(),
                    LIVE_QUESTION_SYSTEM_PROMPT,
                    userPrompt,
                    "live_questions",
                    LIVE_QUESTION_SCHEMA
            );
            log.info("generateLiveQuestions uuid:{}, time:{}, result length:{}", uuid, System.currentTimeMillis() - start, text.length());
            return text;
        } catch (Exception e) {
            throw new RuntimeException("生成现场题目失败", e);
        }
    }

    private static final String DIRECT_FOLLOW_UP_SYSTEM_PROMPT = """
            你是一名专业的大学考核教授。你需要根据原问题和学生回答，直接生成一个深入的追问问题。

            要求：
            1. 必须生成追问，不需要判断是否追问
            2. 追问必须基于原问题和学生回答，不得引入无关主题
            3. 优先追问学生回答中缺少的依据、实现细节、验证数据、关键技术或决策逻辑
            4. 追问难度适中，语言简洁明确
            5. 只输出追问问题本身，不要输出 JSON、标题、解释、编号或 markdown
            """;

    @Retryable(retryFor = RuntimeException.class)
    public String generateFollowUp(String originalQuestion, String answerContent, String followUpStandards) {
        String uuid = java.util.UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        log.info("start generateFollowUp request, uuid:{}, time:{}", uuid, start);

        String userPrompt = """
                【原问题】
                %s

                【学生回答】
                %s

                【追问标准】
                %s

                请直接生成一个追问问题。
                """.formatted(originalQuestion, answerContent, followUpStandards);

        try {
            String text = openAiManager.chatCompletion(
                    openAiManager.getTextModel(),
                    DIRECT_FOLLOW_UP_SYSTEM_PROMPT,
                    userPrompt
            );
            String followUp = normalizeFollowUpQuestion(text);
            log.info("generateFollowUp uuid:{}, time:{}, result length:{}",
                    uuid, System.currentTimeMillis() - start, followUp.length());
            return followUp;
        } catch (Exception e) {
            throw new RuntimeException("生成追问失败", e);
        }
    }

    private static String normalizeFollowUpQuestion(String text) {
        String question = text == null ? "" : text.trim();
        if (question.startsWith("```")) {
            int startIdx = question.indexOf('\n') + 1;
            int endIdx = question.lastIndexOf("```");
            if (startIdx > 0 && endIdx > startIdx) {
                question = question.substring(startIdx, endIdx).trim();
            }
        }
        return StringUtils.strip(question, "\"'“”‘’");
    }

    @Retryable(retryFor = RuntimeException.class)
    public String pickVoiceContent(byte[] voice) {
        long start = System.currentTimeMillis();
        String uuid = java.util.UUID.randomUUID().toString();
        log.info("pickVoiceContent uuid:{}, start time:{}, voiceLength:{}", uuid, start, voice.length);

        try {
            String text = openAiManager.chatCompletionWithAudio(
                    openAiManager.getOmniModel(),
                    VOICE_TRANSCRIPTION_SYSTEM_PROMPT,
                    voice,
                    "wav",
                    "请获取语音的转写内容"
            );

            log.info("pickVoiceContent translate voice uuid:{}, text:{}, time:{}", uuid, text, System.currentTimeMillis() - start);
            if (StringUtils.isBlank(text)) {
                return "没有回答";
            }
            if (containsTraditional(text)) {
                text = convertToSimple(text);
            }
            return text;
        } catch (Exception e) {
            log.error("pickVoiceContent error", e);
            throw new RuntimeException(e);
        }
    }

    private boolean containsTraditional(String text) {
        return ZhConverterUtil.containsTraditional(text);
    }

    private String convertToSimple(String text) {
        return ZhConverterUtil.toSimple(text);
    }

    @Retryable(retryFor = RuntimeException.class)
    public String pickPdfContent(byte[] pdf) {
        long start = System.currentTimeMillis();
        log.info("pickPdfContent start time:{}", start);

        List<OpenAiManager.FileInput> files = List.of(
                new OpenAiManager.FileInput(pdf, "application/pdf"));
        String text = openAiManager.chatCompletionWithFiles(
                openAiManager.getTextModel(),
                PDF_EXTRACTION_SYSTEM_PROMPT,
                "获取转写内容",
                files
        );

        log.info("pickPdfContent translate voice time:{}", System.currentTimeMillis() - start);
        if (StringUtils.isBlank(text)) {
            throw new RuntimeException("PDF内容提取失败，请重新尝试");
        }
        return text;
    }

    @Retryable(retryFor = RuntimeException.class)
    public AssessmentEvaluationDTO summaryAnalysis(String systemPrompt, String prompt,
                                                    String materialContext, String schemaType) {
        String promptWithMaterial = """
                【考试材料上下文】
                %s

                %s
                """.formatted(StringUtils.defaultIfBlank(materialContext, "无"), prompt);
        return summaryAnalysis(systemPrompt, promptWithMaterial, List.of(), schemaType);
    }

    @Retryable(retryFor = RuntimeException.class)
    public AssessmentEvaluationDTO summaryAnalysis(String systemPrompt, String prompt,
                                                    List<byte[]> files, String schemaType) {
        String uuid = java.util.UUID.randomUUID().toString();
        long start = System.currentTimeMillis();

        try {
            List<OpenAiManager.FileInput> fileInputs = new ArrayList<>();
            if (files != null) {
                for (byte[] file : files) {
                    fileInputs.add(new OpenAiManager.FileInput(file, "application/pdf"));
                }
            }

            String schemaJson = switch (schemaType) {
                case "defense_question" -> DEFENSE_QUESTION_EVALUATION_SCHEMA;
                case "defense" -> DEFENSE_ONLY_EVALUATION_SCHEMA;
                case "question" -> QUESTION_ONLY_EVALUATION_SCHEMA;
                default -> throw new IllegalArgumentException("Unknown schema type: " + schemaType);
            };

            log.info("summaryAnalysis uuid:{}, startTime:{}, schemaType:{}", uuid, start, schemaType);

            String resultText;
            if (fileInputs.isEmpty()) {
                resultText = openAiManager.chatCompletionWithSchema(
                        openAiManager.getTextModel(),
                        systemPrompt,
                        prompt,
                        "evaluation_result",
                        schemaJson
                );
            } else {
                resultText = openAiManager.chatCompletionWithFiles(
                        openAiManager.getTextModel(),
                        systemPrompt,
                        prompt,
                        fileInputs
                );
            }

            log.info("summaryAnalysis uuid:{}, time-consuming:{}, responseText length:{}", uuid,
                    System.currentTimeMillis() - start, resultText.length());

            AssessmentEvaluationDTO results = JSONUtil.toBean(resultText, AssessmentEvaluationDTO.class);
            results.verifyIntegrity();
            return results;
        } catch (Exception e) {
            log.error("summaryAnalysis error", e);
            throw new RuntimeException(e);
        }
    }
}
