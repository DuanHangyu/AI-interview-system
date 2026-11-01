package system.assessment.defense.application.service.interview;

import org.junit.jupiter.api.Test;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentQuestionPO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionDrawerTest {

    @Test
    void drawsRequestedCountWithoutDuplicates() {
        List<AssessmentQuestionPO> bank = bank("A", 10);
        List<AssessmentQuestionPO> drawn = QuestionDrawer.draw(bank, 4, new Random(1));
        assertThat(drawn).hasSize(4);
        assertThat(drawn.stream().map(AssessmentQuestionPO::getId).collect(Collectors.toSet())).hasSize(4);
    }

    @Test
    void neverDrawsMoreThanTheBank() {
        assertThat(QuestionDrawer.draw(bank("A", 2), 5, new Random(1))).hasSize(2);
        assertThat(QuestionDrawer.draw(List.of(), 5, new Random(1))).isEmpty();
        assertThat(QuestionDrawer.draw(bank("A", 3), 0, new Random(1))).isEmpty();
    }

    @Test
    void coversEveryDimensionWhenCountAllows() {
        List<AssessmentQuestionPO> bank = new ArrayList<>();
        bank.addAll(bank("技术实现", 8));
        bank.addAll(bank("项目管理", 2));
        bank.addAll(bank("表达能力", 2));
        for (int seed = 0; seed < 50; seed++) {
            List<AssessmentQuestionPO> drawn = QuestionDrawer.draw(bank, 3, new Random(seed));
            Set<String> dimensions = drawn.stream().map(AssessmentQuestionPO::getDimension).collect(Collectors.toSet());
            assertThat(dimensions).containsExactlyInAnyOrder("技术实现", "项目管理", "表达能力");
        }
    }

    @Test
    void differentStudentsGetDifferentSets() {
        List<AssessmentQuestionPO> bank = bank("A", 20);
        Set<List<Integer>> distinct = new HashSet<>();
        for (int seed = 0; seed < 20; seed++) {
            distinct.add(QuestionDrawer.draw(bank, 5, new Random(seed)).stream().map(AssessmentQuestionPO::getId).toList());
        }
        assertThat(distinct.size()).isGreaterThan(15);
    }

    @Test
    void blankDimensionsAreTreatedAsOneGroup() {
        List<AssessmentQuestionPO> bank = bank(null, 3);
        bank.addAll(bank(" ", 3));
        Map<String, Long> counts = QuestionDrawer.draw(bank, 6, new Random(3)).stream()
                .collect(Collectors.groupingBy(q -> String.valueOf(q.getId() / 100), Collectors.counting()));
        assertThat(counts.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(6);
    }

    private static int nextId = 1;

    private static List<AssessmentQuestionPO> bank(String dimension, int size) {
        List<AssessmentQuestionPO> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int id = nextId++;
            list.add(AssessmentQuestionPO.builder().id(id).dimension(dimension).content("题目" + id).build());
        }
        return list;
    }
}
