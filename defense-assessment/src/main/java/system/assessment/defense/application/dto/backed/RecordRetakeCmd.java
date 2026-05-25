package system.assessment.defense.application.dto.backed;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @USER taoHouChao
 * @DATE 14:20 2025/10/20
 */
@Data
@Schema(description = "重新考试命令")
public class RecordRetakeCmd {

    @Schema(description = "考试记录id")
    private Integer recordId;
}
