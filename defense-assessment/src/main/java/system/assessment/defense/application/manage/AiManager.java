package system.assessment.defense.application.manage;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import system.assessment.defense.application.dto.AssessmentEvaluationDTO;
import system.assessment.defense.application.dto.FollowUpItemDTO;
import system.assessment.defense.application.dto.QuestionItemDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * 面向业务的大模型能力：考核评分、辅助出题。底层调用统一走 {@link OpenAiManager}（含限流与重试）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiManager {

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
                    "required": ["id", "strength", "weakness", "suggestion"],
                    "additionalProperties": false,
                    "properties": {
                      "id": {"type": "integer", "description": "问题Id"},
                      "strength": {"type": "string", "description": "本题回答的优势"},
                      "weakness": {"type": "string", "description": "本题回答的不足"},
                      "suggestion": {"type": "string", "description": "本题的改进建议"}
                    }
                  }
                },
                "defense": {
                  "type": "object",
                  "required": ["strength", "weakness", "suggestion"],
                  "additionalProperties": false,
                  "properties": {
                    "strength": {"type": "string", "description": "答辩陈述的优点"},
                    "weakness": {"type": "string", "description": "答辩陈述的不足"},
                    "suggestion": {"type": "string", "description": "答辩陈述的改进建议"}
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
                  "required": ["strength", "weakness", "suggestion"],
                  "additionalProperties": false,
                  "properties": {
                    "strength": {"type": "string", "description": "答辩陈述的优点"},
                    "weakness": {"type": "string", "description": "答辩陈述的不足"},
                    "suggestion": {"type": "string", "description": "答辩陈述的改进建议"}
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
                    "required": ["id", "strength", "weakness", "suggestion"],
                    "additionalProperties": false,
                    "properties": {
                      "id": {"type": "integer", "description": "问题Id"},
                      "strength": {"type": "string", "description": "本题回答的优势"},
                      "weakness": {"type": "string", "description": "本题回答的不足"},
                      "suggestion": {"type": "string", "description": "本题的改进建议"}
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

    /**
     * 按题目评分标准评分的输出结构；%s 处为答辩点评（没有答辩环节时为空）。
     */
    private static final String RUBRIC_EVALUATION_SCHEMA = """
            {
              "type": "object",
              "required": ["result", "analysis", "summary"%s],
              "additionalProperties": false,
              "properties": {
                "result": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "required": ["id", "dimensions", "strength", "weakness", "suggestion"],
                    "additionalProperties": false,
                    "properties": {
                      "id": {"type": "integer", "description": "问题Id"},
                      "dimensions": {
                        "type": "array",
                        "items": {
                          "type": "object",
                          "required": ["name", "check", "hitPoints", "missedPoints", "comment", "level", "score"],
                          "additionalProperties": false,
                          "properties": {
                            "name": {"type": "string", "description": "评分维度名称，与本题评分标准一致"},
                            "check": {"type": "string", "description": "逐条对照最高分档的各条表现，写明每条是否达到及学生原话依据"},
                            "hitPoints": {"type": "array", "items": {"type": "string"}},
                            "missedPoints": {"type": "array", "items": {"type": "string"}},
                            "comment": {"type": "string", "description": "给分依据"},
                            "level": {"type": "string", "description": "所在分档的分数段，如 4-5分"},
                            "score": {"type": "integer", "description": "该维度得分"}
                          }
                        }
                      },
                      "strength": {"type": "string"},
                      "weakness": {"type": "string"},
                      "suggestion": {"type": "string"}
                    }
                  }
                },%s
                "analysis": {
                  "type": "object",
                  "required": ["suggestions", "strengths", "weaknesses"],
                  "additionalProperties": false,
                  "properties": {
                    "suggestions": {"type": "array", "items": {"type": "string"}},
                    "strengths": {"type": "array", "items": {"type": "string"}},
                    "weaknesses": {"type": "array", "items": {"type": "string"}}
                  }
                },
                "summary": {"type": "string", "description": "总结"}
              }
            }
            """;

    private static final String RUBRIC_DEFENSE_SCHEMA = """

                "defense": {
                  "type": "object",
                  "required": ["strength", "weakness", "suggestion"],
                  "additionalProperties": false,
                  "properties": {
                    "strength": {"type": "string"},
                    "weakness": {"type": "string"},
                    "suggestion": {"type": "string"}
                  }
                },""";

    private final OpenAiManager openAiManager;

    private static final String QUESTION_DRAFT_SYSTEM_PROMPT = """
            你是一名经验丰富的大学课程考核出题专家，负责为线上 AI 面试准备题库草稿，供教师审核后使用。
            要求：
            1. 根据考核主题、考核要求、考核标准和考试材料出题；考核标准中的每个维度都要覆盖，题目在各维度间均匀分布
            2. 每道题都是可以在 1~3 分钟内口头作答的开放式问题，表述清晰、无歧义，不包含多个独立小问
            3. 题目之间不能重复或高度相似，难度适中且有区分度
            4. referenceAnswer 给出评分时参考的要点（3~5 条要点，简洁），不是标准答案全文
            5. followUpQuestion 是在学生回答主问题之后可以追问的一个深入问题，不得以主问题的标准答案为前提
            6. 使用简体中文，严格按 JSON 格式输出，不要输出任何额外文字
            JSON 格式：
            {"questions":[{"dimension":"维度名称","content":"题目","referenceAnswer":"参考要点","followUpQuestion":"追问"}]}
            """;

    /**
     * 根据考核信息生成题库草稿（教师端在创建考核时使用，不在学生面试链路上）。
     */
    public List<QuestionItemDTO> generateQuestionDraft(String theme, String requirements, String criteria,
                                                       String materialContext, int count) {
        String userPrompt = """
                【考核主题】
                %s
                【考核要求】
                %s
                【考核标准】
                %s
                【考试材料】
                %s
                请生成 %d 道题。
                """.formatted(StringUtils.defaultIfBlank(theme, "无"),
                StringUtils.defaultIfBlank(requirements, "无"),
                StringUtils.defaultIfBlank(criteria, "无"),
                StringUtils.defaultIfBlank(materialContext, "无"),
                count);
        String text = openAiManager.chatCompletionWithSchema(openAiManager.getTextModel(),
                QUESTION_DRAFT_SYSTEM_PROMPT, userPrompt, "question_draft", "{}");
        return parseQuestionDraft(text);
    }

    static List<QuestionItemDTO> parseQuestionDraft(String text) {
        String json = stripCodeFence(text);
        JSONArray questions = JSONUtil.parseObj(json).getJSONArray("questions");
        List<QuestionItemDTO> result = new ArrayList<>();
        if (questions == null) {
            return result;
        }
        for (int i = 0; i < questions.size(); i++) {
            JSONObject q = questions.getJSONObject(i);
            String content = q == null ? null : StringUtils.trimToNull(q.getStr("content"));
            if (content == null) {
                continue;
            }
            QuestionItemDTO item = new QuestionItemDTO();
            item.setDimension(StringUtils.trimToNull(q.getStr("dimension")));
            item.setContent(content);
            item.setReferenceAnswer(textOf(q.get("referenceAnswer")));
            String followUp = StringUtils.trimToNull(q.getStr("followUpQuestion"));
            item.setFollowUps(followUp == null ? List.of() : List.of(FollowUpItemDTO.builder().content(followUp).build()));
            result.add(item);
        }
        return result;
    }

    /**
     * 对一场面试做整体评分。失败直接抛出，由后台任务负责退避重试。
     */
    public AssessmentEvaluationDTO summaryAnalysis(String systemPrompt, String prompt,
                                                   String materialContext, String schemaType) {
        String promptWithMaterial = """
                【考试材料上下文】
                %s

                %s
                """.formatted(StringUtils.defaultIfBlank(materialContext, "无"), prompt);
        String schemaJson = switch (schemaType) {
            case "defense_question" -> DEFENSE_QUESTION_EVALUATION_SCHEMA;
            case "defense" -> DEFENSE_ONLY_EVALUATION_SCHEMA;
            case "question" -> QUESTION_ONLY_EVALUATION_SCHEMA;
            default -> throw new IllegalArgumentException("Unknown schema type: " + schemaType);
        };
        long start = System.currentTimeMillis();
        String resultText = openAiManager.chatCompletionWithSchema(openAiManager.getTextModel(),
                systemPrompt, promptWithMaterial, "evaluation_result", schemaJson);
        log.info("summaryAnalysis schemaType:{}, cost:{}ms, length:{}", schemaType,
                System.currentTimeMillis() - start, resultText.length());
        AssessmentEvaluationDTO results = JSONUtil.toBean(stripCodeFence(resultText), AssessmentEvaluationDTO.class);
        results.verifyIntegrity();
        return results;
    }

    /**
     * 按题目评分标准评分：模型只给逐题逐维度的分数和点评，总分与维度汇总由调用方计算并校验。
     */
    public AssessmentEvaluationDTO rubricAnalysis(String systemPrompt, String prompt, String materialContext,
                                                  boolean withDefense) {
        String promptWithMaterial = """
                【考试材料上下文】
                %s

                %s
                """.formatted(StringUtils.defaultIfBlank(materialContext, "无"), prompt);
        String schemaJson = RUBRIC_EVALUATION_SCHEMA.formatted(withDefense ? ", \"defense\"" : "",
                withDefense ? RUBRIC_DEFENSE_SCHEMA : "");
        long start = System.currentTimeMillis();
        String resultText = openAiManager.chatCompletionWithSchema(openAiManager.getTextModel(),
                systemPrompt, promptWithMaterial, "rubric_evaluation", schemaJson);
        log.info("rubricAnalysis defense:{}, cost:{}ms, length:{}", withDefense,
                System.currentTimeMillis() - start, resultText.length());
        return JSONUtil.toBean(stripCodeFence(resultText), AssessmentEvaluationDTO.class);
    }

    /**
     * 模型偶尔会把要点输出成数组，统一拼成多行文本
     */
    private static String textOf(Object value) {
        if (value instanceof JSONArray array) {
            return StringUtils.trimToNull(String.join("\n", array.toList(String.class)));
        }
        return value == null ? null : StringUtils.trimToNull(value.toString());
    }

    private static String stripCodeFence(String text) {
        String json = StringUtils.trimToEmpty(text);
        if (json.startsWith("```")) {
            int startIdx = json.indexOf("\n") + 1;
            int endIdx = json.lastIndexOf("```");
            if (endIdx > startIdx) {
                json = json.substring(startIdx, endIdx).trim();
            }
        }
        return json;
    }
}
