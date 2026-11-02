package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "报告中的评分维度")
public class ReportDimensionDTO {

    @Schema(description = "维度名称")
    private String name;

    @Schema(description = "AI 评分")
    private Integer aiScore;

    @Schema(description = "最终分数（教师复核后的分数，未复核时等于 AI 评分）")
    private Integer score;

    @Schema(description = "该维度满分，从考核标准中解析，解析不到时为空")
    private Integer maxScore;

    @Schema(description = "AI 评语")
    private String description;

    @Schema(description = "所在分档，例如“4-5分”")
    private String level;

    @Schema(description = "已答到的考察点")
    private List<String> hitPoints;

    @Schema(description = "未答到或有误的考察点")
    private List<String> missedPoints;
}
