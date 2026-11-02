package system.assessment.defense.application.service.exam;

import cn.hutool.json.JSONUtil;
import org.apache.commons.lang3.StringUtils;
import system.assessment.defense.application.dto.AnalysisBO;
import system.assessment.defense.application.dto.AnalysisDTO;
import system.assessment.defense.application.dto.AssessmentEvaluationDTO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentRecordPO;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 面试得分：AI 评分 = 评分结果中各维度得分之和；最终得分 = 教师复核分（复核过时）否则 AI 评分。
 */
public final class ScoreCalculator {

    private ScoreCalculator() {
    }

    public static AssessmentEvaluationDTO evaluation(StudentAssessmentRecordPO record) {
        if (record == null || StringUtils.isBlank(record.getDefenseResult())) {
            return null;
        }
        try {
            return JSONUtil.toBean(record.getDefenseResult(), AssessmentEvaluationDTO.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static List<AnalysisDTO> dimensions(AssessmentEvaluationDTO evaluation) {
        return Optional.ofNullable(evaluation).map(AssessmentEvaluationDTO::getAnalysis)
                .map(AnalysisBO::getAnalysis).orElse(List.of());
    }

    public static Integer aiScore(StudentAssessmentRecordPO record) {
        List<AnalysisDTO> dims = dimensions(evaluation(record));
        if (dims.isEmpty()) {
            return null;
        }
        return dims.stream().map(AnalysisDTO::getScore).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
    }

    public static Integer finalScore(StudentAssessmentRecordPO record) {
        if (record == null) {
            return null;
        }
        if (StringUtils.isNotBlank(record.getTeacherReview()) && record.getCheckScore() != null) {
            return record.getCheckScore();
        }
        if (record.getCheckScore() != null && record.getCheckScore() > 0) {
            return record.getCheckScore();
        }
        return aiScore(record);
    }
}
