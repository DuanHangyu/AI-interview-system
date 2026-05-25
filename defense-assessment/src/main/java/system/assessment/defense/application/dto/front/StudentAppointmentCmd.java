package system.assessment.defense.application.dto.front;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @USER taoHouChao
 * @DATE 00:29 2025/9/21
 */
@Data
@Schema(description = "学生预约考核命令")
public class StudentAppointmentCmd {

    @Schema(description = "考核id")
    private Integer assessmentId;

    @Schema(description = "预约时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime timePeriod;
}
