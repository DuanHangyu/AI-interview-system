package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @USER taoHouChao
 * @DATE 16:19 2025/6/23
 */
@Data
@Schema(description = "答案评估结果")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AnswerEvaluationDTO {

    @Schema(description = "问答id")
    private Integer id;

    @Schema(description = "问题")
    private String question;

    @Schema(description = "答案")
    private String answer;

    @Schema(description = "答案文件")
    private String answerFile;

    @Schema(description = "追问及回答，按提问顺序")
    private List<FollowUpAnswerDTO> followUps;

    @Schema(description = "按本题评分标准的逐维度评分（未设置评分标准时为空）")
    private List<QuestionDimensionDTO> dimensions;

    @Schema(description = "本题回答的优势")
    private String strength;

    @Schema(description = "本题回答的不足")
    private String weakness;

    @Schema(description = "本题的改进建议")
    private String suggestion;
}
