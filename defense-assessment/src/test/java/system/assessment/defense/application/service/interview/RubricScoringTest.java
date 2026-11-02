package system.assessment.defense.application.service.interview;

import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import system.assessment.defense.application.dto.AnalysisBO;
import system.assessment.defense.application.dto.AnalysisDTO;
import system.assessment.defense.application.dto.AnswerEvaluationDTO;
import system.assessment.defense.application.dto.AssessmentEvaluationDTO;
import system.assessment.defense.application.dto.QuestionDimensionDTO;
import system.assessment.defense.application.dto.QuestionItemDTO;
import system.assessment.defense.application.dto.QuestionRubric;
import system.assessment.defense.infrastructure.exception.BusinessException;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentQuestionAnswerPO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RubricScoringTest {

    @Test
    void alignsToRubricClampsScoresAndSumsDimensions() {
        StudentAssessmentQuestionAnswerPO main = main(12, rubric(5, 5));
        AssessmentEvaluationDTO evaluation = evaluation(item(12,
                dim("知识", "4-5分", 9, List.of("意识的本质"), List.of()),
                dim("能力维度", "2-3分", 3, List.of(), List.of("案例分析"))));

        RubricScoring.apply(evaluation, List.of(main));

        List<QuestionDimensionDTO> dims = evaluation.getResult().get(0).getDimensions();
        assertThat(dims).extracting(QuestionDimensionDTO::getName).containsExactly("知识维度", "能力维度");
        assertThat(dims).extracting(QuestionDimensionDTO::getScore).containsExactly(5, 3);
        List<AnalysisDTO> analysis = evaluation.getAnalysis().getAnalysis();
        assertThat(analysis).extracting(AnalysisDTO::getMaxScore).containsExactly(5, 5);
        assertThat(analysis.get(0).getLevel()).isEqualTo("4-5分");
        assertThat(analysis.get(1).getMissedPoints()).containsExactly("案例分析");
        assertThat(analysis.stream().mapToInt(AnalysisDTO::getScore).sum()).isEqualTo(8);
    }

    @Test
    void aggregatesSameDimensionAcrossQuestions() {
        AssessmentEvaluationDTO evaluation = evaluation(
                item(1, dim("知识维度", "2-3分", 2, List.of(), List.of()), dim("能力维度", "0-1分", 1, List.of(), List.of())),
                item(2, dim("知识维度", "4-5分", 4, List.of(), List.of()), dim("能力维度", "2-3分", 3, List.of(), List.of())));
        RubricScoring.apply(evaluation, List.of(main(1, rubric(5, 5)), main(2, rubric(5, 5))));
        List<AnalysisDTO> analysis = evaluation.getAnalysis().getAnalysis();
        assertThat(analysis).extracting(AnalysisDTO::getScore).containsExactly(6, 4);
        assertThat(analysis).extracting(AnalysisDTO::getMaxScore).containsExactly(10, 10);
        assertThat(analysis.get(0).getLevel()).isNull();
    }

    @Test
    void missingQuestionOrDimensionFailsForRetry() {
        assertThatThrownBy(() -> RubricScoring.apply(evaluation(), List.of(main(1, rubric(5, 5)))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> RubricScoring.apply(
                evaluation(item(1, dim("知识维度", "4-5分", 4, List.of(), List.of()))), List.of(main(1, rubric(5, 5)))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void picksFollowUpsKeepingOrder() {
        List<String> all = List.of("F1", "F2", "F3");
        assertThat(QuestionDrawer.pickFollowUps(all, null, new Random(1))).containsExactly("F1", "F2", "F3");
        assertThat(QuestionDrawer.pickFollowUps(all, 3, new Random(1))).containsExactly("F1", "F2", "F3");
        assertThat(QuestionDrawer.pickFollowUps(all, 0, new Random(1))).hasSize(3);
        assertThat(QuestionDrawer.pickFollowUps(List.of(), 2, new Random(1))).isEmpty();
        Set<String> seen = new HashSet<>();
        for (int seed = 0; seed < 50; seed++) {
            List<String> two = QuestionDrawer.pickFollowUps(all, 2, new Random(seed));
            assertThat(two).hasSize(2).isSorted();
            List<String> one = QuestionDrawer.pickFollowUps(all, 1, new Random(seed));
            assertThat(one).hasSize(1);
            seen.addAll(one);
        }
        assertThat(seen).containsExactlyInAnyOrder("F1", "F2", "F3");
    }

    @Test
    void rubricTotalRequiresAllQuestionsWithSameTotal() {
        AtomicInteger total = new AtomicInteger();
        QuestionItemDTO a = QuestionItemDTO.builder().content("A").rubric(rubric(5, 5)).build();
        QuestionItemDTO b = QuestionItemDTO.builder().content("B").rubric(rubric(5, 5)).build();
        QuestionBankService.applyRubricTotal(total::set, 1, List.of(a, b));
        assertThat(total.get()).isEqualTo(10);
        QuestionBankService.applyRubricTotal(total::set, 2, List.of(a, b));
        assertThat(total.get()).isEqualTo(20);

        QuestionItemDTO plain = QuestionItemDTO.builder().content("C").build();
        assertThatThrownBy(() -> QuestionBankService.applyRubricTotal(total::set, 1, List.of(a, plain)))
                .isInstanceOf(BusinessException.class);
        QuestionItemDTO bigger = QuestionItemDTO.builder().content("D").rubric(rubric(5, 10)).build();
        assertThatThrownBy(() -> QuestionBankService.applyRubricTotal(total::set, 1, List.of(a, bigger)))
                .isInstanceOf(BusinessException.class);
        total.set(-1);
        QuestionBankService.applyRubricTotal(total::set, 1, List.of(plain));
        assertThat(total.get()).isEqualTo(-1);
    }

    private static QuestionRubric rubric(int knowledge, int ability) {
        return QuestionRubric.builder().dimensions(List.of(
                QuestionRubric.Dimension.builder().name("知识维度").maxScore(knowledge).build(),
                QuestionRubric.Dimension.builder().name("能力维度").maxScore(ability).build())).build();
    }

    private static StudentAssessmentQuestionAnswerPO main(int id, QuestionRubric rubric) {
        return StudentAssessmentQuestionAnswerPO.builder().id(id).sortIndex(id).rubric(JSONUtil.toJsonStr(rubric)).build();
    }

    private static QuestionDimensionDTO dim(String name, String level, int score, List<String> hit, List<String> missed) {
        return QuestionDimensionDTO.builder().name(name).level(level).score(score).hitPoints(hit).missedPoints(missed)
                .comment("依据").build();
    }

    private static AnswerEvaluationDTO item(int id, QuestionDimensionDTO... dims) {
        return AnswerEvaluationDTO.builder().id(id).dimensions(List.of(dims)).strength("s").build();
    }

    private static AssessmentEvaluationDTO evaluation(AnswerEvaluationDTO... items) {
        AssessmentEvaluationDTO evaluation = new AssessmentEvaluationDTO();
        evaluation.setResult(new ArrayList<>(List.of(items)));
        AnalysisBO bo = new AnalysisBO();
        bo.setStrengths(List.of("优点"));
        evaluation.setAnalysis(bo);
        return evaluation;
    }
}
