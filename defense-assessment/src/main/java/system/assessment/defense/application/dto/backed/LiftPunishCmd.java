package system.assessment.defense.application.dto.backed;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @USER taoHouChao
 * @DATE 00:01 2025/9/24
 */
@Data
@Schema(description = "解除惩罚")
public class LiftPunishCmd {

    @Schema(description = "预约id")
    private Integer id;
}
