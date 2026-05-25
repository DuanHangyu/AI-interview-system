package system.assessment.defense.application.dto.backed;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @USER taoHouChao
 * @DATE 18:53 2025/10/19
 */
@Data
@Schema(description = "取消学生预约命令")
public class CancelStudentAppointmentCmd {

    @Schema(description = "预约id")
    private Integer appointmentId;
}
