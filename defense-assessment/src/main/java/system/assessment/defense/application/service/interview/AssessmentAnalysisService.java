package system.assessment.defense.application.service.interview;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import system.assessment.defense.application.dto.AssessmentEvaluationDTO;
import system.assessment.defense.application.dto.QuestionRubric;
import system.assessment.defense.application.manage.AiManager;
import system.assessment.defense.application.service.AssessmentMaterialService;
import system.assessment.defense.application.task.TaskHandler;
import system.assessment.defense.infrastructure.common.PromptTemplateConstant;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentSettingPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentQuestionAnswerPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentRecordPO;
import system.assessment.defense.infrastructure.repository.dao.service.AssessmentSettingService;
import system.assessment.defense.infrastructure.repository.dao.service.StudentAssessmentQuestionAnswerService;
import system.assessment.defense.infrastructure.repository.dao.service.StudentAssessmentRecordService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 面试评分：等所有录音转写完成后，把答辩内容、问答内容、参考答案和考试材料交给大模型评分。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentAnalysisService {

    static final int RECORD_DONE = 1;
    static final int RECORD_ANALYZING = 2;

    private final StudentAssessmentRecordService recordService;
    private final StudentAssessmentQuestionAnswerService answerService;
    private final AssessmentSettingService settingService;
    private final AssessmentMaterialService materialService;
    private final AiManager aiManager;

    public void analyze(Integer recordId) {
        StudentAssessmentRecordPO record = recordService.getById(recordId);
        if (record == null || !Objects.equals(record.getState(), RECORD_ANALYZING)) {
            // 已评分完成，或被教师设为重考，不再处理
            return;
        }
        AssessmentSettingPO setting = settingService.findById(record.getAssessmentId()).orElse(null);
        if (setting == null) {
            return;
        }
        List<StudentAssessmentQuestionAnswerPO> rows = answerService
                .listByAssessmentIdAndStudentId(record.getAssessmentId(), record.getStudentId());
        ensureTranscribed(record, rows);
        List<StudentAssessmentQuestionAnswerPO> answers = AnswerRows.mains(rows);
        Map<Integer, List<StudentAssessmentQuestionAnswerPO>> followUps = AnswerRows.followUps(rows);

        boolean defense = Boolean.TRUE.equals(setting.getDefense());
        boolean question = Boolean.TRUE.equals(setting.getQuestion());
        String materialContext = materialService.getOrParseMaterialContext(setting);
        AssessmentEvaluationDTO result;
        if (useRubric(question, answers)) {
            // 每道题按自己的评分标准逐维度评分，总分由系统汇总
            String prompt = buildPrompt(defense, true, record, answers, followUps, true);
            String system = String.format(PromptTemplateConstant.RUBRIC_ANALYSIS_TEMPLATE_PROMPT,
                    defense ? PromptTemplateConstant.RUBRIC_DEFENSE_RULE : "",
                    defense ? PromptTemplateConstant.RUBRIC_DEFENSE_EXAMPLE : "");
            result = aiManager.rubricAnalysis(system, prompt, materialContext, defense);
            RubricScoring.apply(result, answers);
            result.verifyIntegrity();
        } else {
            String schemaType = defense && question ? "defense_question" : (defense ? "defense" : "question");
            String prompt = buildPrompt(defense, question, record, answers, followUps, false);
            result = aiManager.summaryAnalysis(buildSystemInstruction(setting, answers),
                    prompt, materialContext, schemaType);
        }
        recordService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                .set(StudentAssessmentRecordPO::getDefenseResult, JSONUtil.toJsonStr(result))
                .set(StudentAssessmentRecordPO::getScore, result.getValue().stream()
                        .map(v -> v.getValue() == null ? 0 : v.getValue()).mapToInt(Integer::intValue).sum())
                .set(StudentAssessmentRecordPO::getState, RECORD_DONE)
                .set(StudentAssessmentRecordPO::getReAnalysis, false)
                .set(StudentAssessmentRecordPO::getUpdateTime, LocalDateTime.now())
                .eq(StudentAssessmentRecordPO::getId, recordId)
                .eq(StudentAssessmentRecordPO::getState, RECORD_ANALYZING));
        log.info("assessment analyzed, recordId:{}, studentId:{}, assessmentId:{}", recordId,
                record.getStudentId(), record.getAssessmentId());
    }

    /**
     * 评分彻底失败：保持“分析中”状态但清掉进行中标记，教师端可手动重新分析。
     */
    public void markFailed(Integer recordId) {
        recordService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                .set(StudentAssessmentRecordPO::getReAnalysis, false)
                .set(StudentAssessmentRecordPO::getUpdateTime, LocalDateTime.now())
                .eq(StudentAssessmentRecordPO::getId, recordId));
    }

    private static void ensureTranscribed(StudentAssessmentRecordPO record, List<StudentAssessmentQuestionAnswerPO> answers) {
        if (StringUtils.isNotBlank(record.getDefenseVoice()) && record.getDefenseContent() == null) {
            throw new TaskHandler.DeferException("答辩录音转写中", 10);
        }
        for (StudentAssessmentQuestionAnswerPO answer : answers) {
            if (StringUtils.isNotBlank(answer.getAnswerVoice()) && answer.getAnswer() == null) {
                throw new TaskHandler.DeferException("回答录音转写中", 10);
            }
        }
    }

    /**
     * 抽中的每道题都带评分标准时，按题目评分标准评分；否则按考核的整体考核标准评分（兼容旧考核）
     */
    static boolean useRubric(boolean question, List<StudentAssessmentQuestionAnswerPO> mains) {
        return question && !mains.isEmpty() && mains.stream().allMatch(m -> QuestionRubric.parse(m.getRubric()) != null);
    }

    static String buildPrompt(boolean defense, boolean question, StudentAssessmentRecordPO record,
                              List<StudentAssessmentQuestionAnswerPO> answers,
                              Map<Integer, List<StudentAssessmentQuestionAnswerPO>> followUps, boolean rubric) {
        StringBuilder prompt = new StringBuilder();
        if (defense && question) {
            prompt.append("对学生的答辩和考核问答进行考核\n");
        } else if (defense) {
            prompt.append("对学生的答辩进行考核\n");
        } else {
            prompt.append("对学生考核问答进行考核\n");
        }
        prompt.append("【学生答辩】\n")
                .append(defense ? StringUtils.defaultIfBlank(record.getDefenseContent(), "没有回答") : "无")
                .append("\n【考核问答】\n");
        if (!question || answers.isEmpty()) {
            prompt.append("空\n");
            return prompt.toString();
        }
        for (StudentAssessmentQuestionAnswerPO answer : answers) {
            prompt.append("问题Id：").append(answer.getId()).append('\n');
            if (StringUtils.isNotBlank(answer.getDimension())) {
                prompt.append("考核维度：").append(answer.getDimension()).append('\n');
            }
            prompt.append("问题：").append(answer.getQuestion()).append('\n');
            QuestionRubric questionRubric = rubric ? QuestionRubric.parse(answer.getRubric()) : null;
            if (questionRubric != null) {
                prompt.append("【本题评分标准】\n").append(questionRubric.toPromptText());
            }
            if (StringUtils.isNotBlank(answer.getReferenceAnswer())) {
                prompt.append("参考答案：").append(answer.getReferenceAnswer()).append('\n');
            }
            prompt.append("回答：").append(StringUtils.defaultIfBlank(answer.getAnswer(), "没有回答")).append('\n');
            List<StudentAssessmentQuestionAnswerPO> follows = followUps.getOrDefault(answer.getId(), List.of());
            for (int i = 0; i < follows.size(); i++) {
                StudentAssessmentQuestionAnswerPO follow = follows.get(i);
                String label = follows.size() == 1 ? "追问" : "追问" + (i + 1);
                prompt.append(label).append("：").append(follow.getQuestion()).append('\n');
                if (rubric && StringUtils.isNotBlank(follow.getFollowIntent())) {
                    prompt.append(label).append("考察意图：").append(follow.getFollowIntent()).append('\n');
                }
                prompt.append(label).append("回答：").append(StringUtils.defaultIfBlank(follow.getAnswer(), "没有回答"))
                        .append('\n');
            }
            prompt.append('\n');
        }
        return prompt.toString();
    }

    private static String buildSystemInstruction(AssessmentSettingPO setting, List<StudentAssessmentQuestionAnswerPO> answers) {
        boolean hasReference = answers.stream().anyMatch(a -> StringUtils.isNotBlank(a.getReferenceAnswer()));
        String reference = hasReference ? "见【考核问答】中每道题的“参考答案”" : "空";
        String criteria = StringUtils.defaultIfBlank(setting.getAssessmentCriteria(), "无");
        if (Boolean.TRUE.equals(setting.getDefense()) && Boolean.TRUE.equals(setting.getQuestion())) {
            return String.format(PromptTemplateConstant.DEFENSE_AND_QUESTION_ANALYSIS_TEMPLATE_PROMPT, criteria, reference);
        } else if (Boolean.TRUE.equals(setting.getDefense())) {
            return String.format(PromptTemplateConstant.DEFENSE_ANALYSIS_TEMPLATE_PROMPT, criteria, reference);
        }
        return String.format(PromptTemplateConstant.QUESTION_ANALYSIS_TEMPLATE_PROMPT, criteria, reference);
    }
}
