package system.assessment.defense.application.service.interview;

import org.apache.commons.lang3.StringUtils;
import system.assessment.defense.application.dto.AnalysisBO;
import system.assessment.defense.application.dto.AnalysisDTO;
import system.assessment.defense.application.dto.AnswerEvaluationDTO;
import system.assessment.defense.application.dto.AssessmentEvaluationDTO;
import system.assessment.defense.application.dto.QuestionDimensionDTO;
import system.assessment.defense.application.dto.QuestionRubric;
import system.assessment.defense.infrastructure.emuns.ErrorCodeEnums;
import system.assessment.defense.infrastructure.exception.BusinessException;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentQuestionAnswerPO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 按题目评分标准评分的结果整理：把模型的逐题逐维度评分对齐到评分标准（维度名、满分以标准为准，分数截断到满分以内），
 * 再按维度汇总成整场面试的维度得分。总分 = 各维度得分之和，不采用模型自己算的总分。
 */
public final class RubricScoring {

    private RubricScoring() {
    }

    /**
     * @param mains 主问题行（带评分标准快照），按题序排列
     * @throws BusinessException 模型漏评了某道题或某个维度（由后台任务重试）
     */
    public static void apply(AssessmentEvaluationDTO evaluation, List<StudentAssessmentQuestionAnswerPO> mains) {
        Map<Integer, AnswerEvaluationDTO> byId = Optional.ofNullable(evaluation.getResult()).orElse(List.of()).stream()
                .filter(r -> r.getId() != null)
                .collect(Collectors.toMap(AnswerEvaluationDTO::getId, Function.identity(), (a, b) -> a));
        List<AnswerEvaluationDTO> aligned = new ArrayList<>();
        Map<String, List<QuestionDimensionDTO>> byDimension = new LinkedHashMap<>();
        for (StudentAssessmentQuestionAnswerPO main : mains) {
            QuestionRubric rubric = QuestionRubric.parse(main.getRubric());
            AnswerEvaluationDTO item = byId.get(main.getId());
            if (rubric == null || item == null) {
                throw new BusinessException(ErrorCodeEnums.ANALYSIS_CHECK_FAIL);
            }
            List<QuestionDimensionDTO> dims = new ArrayList<>();
            for (QuestionRubric.Dimension d : rubric.getDimensions()) {
                QuestionDimensionDTO scored = match(item.getDimensions(), d.getName());
                if (scored == null) {
                    throw new BusinessException(ErrorCodeEnums.ANALYSIS_CHECK_FAIL);
                }
                int score = Math.max(0, Math.min(d.getMaxScore(), Optional.ofNullable(scored.getScore()).orElse(0)));
                QuestionDimensionDTO dim = QuestionDimensionDTO.builder()
                        .name(d.getName())
                        .level(StringUtils.trimToNull(scored.getLevel()))
                        .score(score)
                        .maxScore(d.getMaxScore())
                        .hitPoints(clean(scored.getHitPoints()))
                        .missedPoints(clean(scored.getMissedPoints()))
                        .comment(StringUtils.trimToNull(scored.getComment()))
                        .build();
                dims.add(dim);
                byDimension.computeIfAbsent(d.getName(), k -> new ArrayList<>()).add(dim);
            }
            item.setDimensions(dims);
            aligned.add(item);
        }
        evaluation.setResult(aligned);

        boolean single = mains.size() == 1;
        List<AnalysisDTO> analysis = new ArrayList<>();
        byDimension.forEach((name, dims) -> {
            AnalysisDTO dto = new AnalysisDTO();
            dto.setName(name);
            dto.setScore(dims.stream().mapToInt(QuestionDimensionDTO::getScore).sum());
            dto.setMaxScore(dims.stream().mapToInt(QuestionDimensionDTO::getMaxScore).sum());
            dto.setLevel(single ? dims.get(0).getLevel() : null);
            dto.setHitPoints(dims.stream().flatMap(d -> d.getHitPoints().stream()).distinct().toList());
            dto.setMissedPoints(dims.stream().flatMap(d -> d.getMissedPoints().stream()).distinct().toList());
            dto.setDescription(single ? dims.get(0).getComment() : joinComments(dims));
            analysis.add(dto);
        });
        AnalysisBO bo = Optional.ofNullable(evaluation.getAnalysis()).orElseGet(AnalysisBO::new);
        bo.setAnalysis(analysis);
        bo.setStrengths(clean(bo.getStrengths()));
        bo.setWeaknesses(clean(bo.getWeaknesses()));
        bo.setSuggestions(clean(bo.getSuggestions()));
        evaluation.setAnalysis(bo);
    }

    /**
     * 先按名称精确匹配，再互相包含匹配（模型可能写成“知识”或“知识维度评分”）
     */
    static QuestionDimensionDTO match(List<QuestionDimensionDTO> scored, String name) {
        if (scored == null) {
            return null;
        }
        String target = StringUtils.trim(name);
        return scored.stream().filter(s -> Objects.equals(StringUtils.trim(s.getName()), target)).findFirst()
                .or(() -> scored.stream()
                        .filter(s -> StringUtils.isNotBlank(s.getName()))
                        .filter(s -> target.contains(s.getName().trim()) || s.getName().contains(target))
                        .findFirst())
                .orElse(null);
    }

    private static String joinComments(List<QuestionDimensionDTO> dims) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dims.size(); i++) {
            if (dims.get(i).getComment() != null) {
                sb.append(sb.isEmpty() ? "" : "\n").append("第 ").append(i + 1).append(" 题：").append(dims.get(i).getComment());
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static List<String> clean(List<String> items) {
        return items == null ? List.of() : items.stream()
                .map(StringUtils::trimToNull)
                .filter(Objects::nonNull)
                .toList();
    }
}
