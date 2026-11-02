package system.assessment.defense.infrastructure.common;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CriteriaParserTest {

    @Test
    void parsesDimensionMaxScores() {
        Map<String, Integer> max = CriteriaParser.maxScores(
                "技术实现（40分）：方案合理性与深度；工程质量(30 分)：测试与稳定性；\n1.综合能力（30分）：表达与反思");
        assertThat(max).containsEntry("技术实现", 40).containsEntry("工程质量", 30).containsEntry("综合能力", 30);
        assertThat(CriteriaParser.maxScoreOf(max, "技术实现")).isEqualTo(40);
        assertThat(CriteriaParser.maxScoreOf(max, "综合能力评估")).isEqualTo(30);
        assertThat(CriteriaParser.maxScoreOf(max, "创新性")).isNull();
        assertThat(CriteriaParser.maxScores(null)).isEmpty();
    }
}
