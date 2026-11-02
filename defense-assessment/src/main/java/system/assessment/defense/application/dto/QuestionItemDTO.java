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
@Schema(description = "题库中的一道题")
public class QuestionItemDTO {

    @Schema(description = "题目 id，新增时为空")
    private Integer id;

    @Schema(description = "考核维度")
    private String dimension;

    @Schema(description = "题目内容")
    private String content;

    @Schema(description = "参考答案 / 评分要点")
    private String referenceAnswer;

    @Schema(description = "本题评分标准，为空时使用考核的整体考核标准")
    private QuestionRubric rubric;

    @Schema(description = "预制追问，按提问顺序排列")
    private List<FollowUpItemDTO> followUps;

    @Schema(description = "题目播报音频是否已生成（只读）")
    private Boolean audioReady;
}
