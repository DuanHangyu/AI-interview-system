package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @USER taoHouChao
 * @DATE 15:53 2025/8/14
 */
@Data
@Schema(description = "修改密码参数")
public class ChangePasswordDTO {

    @Schema(description = "密码")
    private String password;
}
