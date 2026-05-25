package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @USER taoHouChao
 * @DATE 14:18 2025/8/12
 */
@Data
@Schema(description = "登录参数")
public class LoginDTO {

    @Schema(description = "账号")
    private String account;

    @Schema(description = "密码")
    private String password;
}
