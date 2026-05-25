package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import system.assessment.defense.infrastructure.emuns.ErrorCodeEnums;
import system.assessment.defense.infrastructure.exception.BusinessException;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentQuestionAnswerPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentRecordPO;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @USER taoHouChao
 * @DATE 23:39 2025/8/17
 */
@Data
public class AssessmentEvaluationDTO {

    @Schema(description = "问答")
    private List<AnswerEvaluationDTO> result;

    @Schema(description = "考核信息")
    private DefenseDTO defense;

    @Schema(description = "分析结果")
    private AnalysisBO analysis;

    @Schema(description = "总结")
    private String summary;

    public List<ValueDTO> getValue() {
        return analysis.getAnalysis().stream()
                .map(item -> new ValueDTO(item.getName(), item.getScore()))
                .toList();
    }

    public void fillEmptyValue(Map<Integer, StudentAssessmentQuestionAnswerPO> quesitonAnswerIdMap, StudentAssessmentRecordPO recordPO) {
        if (CollectionUtils.isNotEmpty(result)) {
            for (AnswerEvaluationDTO evaluation : result) {
                Optional.ofNullable(quesitonAnswerIdMap.get(evaluation.getId()))
                        .ifPresent(item -> {
                            if (StringUtils.isBlank(evaluation.getQuestion())) {
                                evaluation.setQuestion(item.getQuestion());
                            }
                            if (StringUtils.isBlank(evaluation.getAnswer())) {
                                evaluation.setAnswer(item.getAnswer());
                            }
                            if (StringUtils.isBlank(evaluation.getFollowQuestion())) {
                                evaluation.setFollowQuestion(item.getFollowQuestion());
                            }
                            if (StringUtils.isBlank(evaluation.getFollowAnswer())) {
                                evaluation.setFollowAnswer(item.getFollowAnswer());
                            }
                        });
            }
        }
        Optional.ofNullable(defense)
                .ifPresent(item -> {
                    if (StringUtils.isBlank(item.getDefenseAnswer())) {
                        item.setDefenseAnswer(recordPO.getDefenseContent());
                    }
                });
    }

    /**
     * 验证数据完整性
     */
    public void verifyIntegrity() {
        if (analysis == null || CollectionUtils.isEmpty(analysis.getAnalysis())) {
            throw new BusinessException(ErrorCodeEnums.ANALYSIS_CHECK_FAIL);
        }
        int count = (int) analysis.getAnalysis().stream().map(AnalysisDTO::getName).distinct().count();
        if (count != analysis.getAnalysis().size()) {
            throw new BusinessException(ErrorCodeEnums.ANALYSIS_CHECK_FAIL);
        }
    }
}
