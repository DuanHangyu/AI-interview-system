package system.assessment.defense.application.dto;

import cn.hutool.json.JSONUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 一道题自己的评分标准：若干维度（如知识、能力、素养、价值），每个维度有满分、核心考察点和分档表现。
 * 以 JSON 存在 assessment_question.rubric，学生抽题时快照到答题记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "题目评分标准")
public class QuestionRubric {

    @Schema(description = "评分维度")
    private List<Dimension> dimensions;

    @Schema(description = "评分说明，例如经追问修正可酌情给分")
    private String notes;

    public int totalScore() {
        return dimensions == null ? 0 : dimensions.stream()
                .map(Dimension::getMaxScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    public boolean isEmpty() {
        return dimensions == null || dimensions.isEmpty();
    }

    public static QuestionRubric parse(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        QuestionRubric rubric = JSONUtil.toBean(json, QuestionRubric.class);
        return rubric == null || rubric.isEmpty() ? null : rubric;
    }

    /**
     * 渲染成评分提示词中的文本
     */
    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        for (Dimension d : dimensions) {
            sb.append("◆ ").append(d.getName()).append("（满分 ").append(d.getMaxScore()).append(" 分）");
            if (StringUtils.isNotBlank(d.getFocus())) {
                sb.append("：").append(d.getFocus());
            }
            sb.append('\n');
            if (d.getPoints() != null && !d.getPoints().isEmpty()) {
                sb.append("  核心考察点：\n");
                for (int i = 0; i < d.getPoints().size(); i++) {
                    sb.append("  ").append(i + 1).append(". ").append(d.getPoints().get(i)).append('\n');
                }
            }
            if (d.getLevels() != null && !d.getLevels().isEmpty()) {
                sb.append("  分档表现：\n");
                for (Level level : d.getLevels()) {
                    sb.append("  【").append(level.getRange()).append("】").append(level.getDescription()).append('\n');
                }
            }
        }
        if (StringUtils.isNotBlank(notes)) {
            sb.append("◆ 评分说明：").append(notes).append('\n');
        }
        return sb.toString();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Dimension {

        @Schema(description = "维度名称，例如“知识维度”")
        private String name;

        @Schema(description = "满分")
        private Integer maxScore;

        @Schema(description = "考察重点，例如“马克思主义理论基础的准确性与关联性”")
        private String focus;

        @Schema(description = "核心考察点")
        private List<String> points;

        @Schema(description = "分档表现，从高分档到低分档")
        private List<Level> levels;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Level {

        @Schema(description = "分数段，例如“4-5分”")
        private String range;

        @Schema(description = "该档的具体表现")
        private String description;
    }
}
