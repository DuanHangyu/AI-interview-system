package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * @USER taoHouChao
 * @DATE 21:01 2025/6/16
 */
@Data
@Schema(description = "分析结果")
public class AnalysisDTO {

    @Schema(description = "指标名")
    private String name;

    @Schema(description = "得分")
    private Integer score;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "满分；按题目评分标准评分时由标准给出，否则为空（从考核标准文本中解析）")
    private Integer maxScore;

    @Schema(description = "所在分档（按题目评分标准评分且只有一道题时）")
    private String level;

    @Schema(description = "已答到的考察点")
    private List<String> hitPoints;

    @Schema(description = "未答到或有误的考察点")
    private List<String> missedPoints;
}
