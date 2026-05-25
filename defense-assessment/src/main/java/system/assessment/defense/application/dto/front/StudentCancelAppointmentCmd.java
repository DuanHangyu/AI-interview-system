package system.assessment.defense.application.dto.front;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @USER taoHouChao
 * @DATE 18:47 2025/10/19
 */
@Data
@Schema(description = "取消预约命令")
public class StudentCancelAppointmentCmd {

    @Schema(description = "考核id")
    private Integer assessmentId;
}
