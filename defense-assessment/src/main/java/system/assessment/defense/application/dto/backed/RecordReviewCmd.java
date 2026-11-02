package system.assessment.defense.application.dto.backed;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import system.assessment.defense.application.dto.TeacherReviewBO;

import java.util.List;

@Data
@Schema(description = "教师复核评分")
public class RecordReviewCmd {

    @NotNull(message = "记录ID不能为空")
    @Schema(description = "考核记录id")
    private Integer recordId;

    @Schema(description = "各维度调整后的分数，维度名称需与 AI 评分维度一致")
    private List<TeacherReviewBO.DimensionScore> dimensions;

    @Schema(description = "教师评语")
    private String comment;
}
