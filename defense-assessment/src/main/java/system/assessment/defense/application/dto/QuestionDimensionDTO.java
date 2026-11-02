package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 按题目评分标准对一道题某个维度的评分结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "单题单维度评分")
public class QuestionDimensionDTO {

    @Schema(description = "维度名称")
    private String name;

    @Schema(description = "所在分档，例如“4-5分”")
    private String level;

    @Schema(description = "得分")
    private Integer score;

    @Schema(description = "满分")
    private Integer maxScore;

    @Schema(description = "已答到的考察点")
    private List<String> hitPoints;

    @Schema(description = "未答到或有误的考察点")
    private List<String> missedPoints;

    @Schema(description = "给分依据")
    private String comment;
}
