package system.assessment.defense.application.service.interview;

import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentQuestionAnswerPO;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 答题记录里主问题和追问各占一行（追问的 parent_id 指向主问题行），这里负责把它们分开、排好序。
 */
public final class AnswerRows {

    private AnswerRows() {
    }

    /**
     * 主问题行，按题序排列
     */
    public static List<StudentAssessmentQuestionAnswerPO> mains(List<StudentAssessmentQuestionAnswerPO> rows) {
        return rows.stream()
                .filter(r -> r.getParentId() == null)
                .sorted(Comparator.comparing(StudentAssessmentQuestionAnswerPO::getSortIndex,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * 主问题行 id → 该题的追问行（按追问序号排列）
     */
    public static Map<Integer, List<StudentAssessmentQuestionAnswerPO>> followUps(List<StudentAssessmentQuestionAnswerPO> rows) {
        return rows.stream()
                .filter(r -> r.getParentId() != null)
                .sorted(Comparator.comparing(StudentAssessmentQuestionAnswerPO::getFollowIndex,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.groupingBy(StudentAssessmentQuestionAnswerPO::getParentId));
    }
}
