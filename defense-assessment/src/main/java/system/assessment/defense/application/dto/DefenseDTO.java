package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @USER taoHouChao
 * @DATE 15:06 2025/8/22
 */
@Data
@Schema(description = "考核分析结果")
public class DefenseDTO {

    @Schema(description = "答辩内容")
    private String defenseAnswer;

    @Schema(description = "答辩语音")
    private String defenseAnswerFile;

    @Schema(description = "答辩陈述的优点")
    private String strength;

    @Schema(description = "答辩陈述的不足")
    private String weakness;

    @Schema(description = "答辩陈述的改进建议")
    private String suggestion;
}
