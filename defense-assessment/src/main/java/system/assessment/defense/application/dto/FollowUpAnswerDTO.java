package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 报告中一道题的一个追问及学生回答
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "追问与回答")
public class FollowUpAnswerDTO {

    @Schema(description = "追问内容")
    private String question;

    @Schema(description = "回答（语音转写）")
    private String answer;

    @Schema(description = "回答录音")
    private String answerFile;
}
