package system.assessment.defense.application.dto.backed;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @USER taoHouChao
 * @DATE 17:15 2025/8/19
 */
@Data
@Schema(description = "学生记录修改得分")
public class StudentRecordChangeScoreCmd {

    @Schema(description = "学生记录id")
    private Integer id;

    @Schema(description = "教师核分")
    private BigDecimal checkScore;
}
