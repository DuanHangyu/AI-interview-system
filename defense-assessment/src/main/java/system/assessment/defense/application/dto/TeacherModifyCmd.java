package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @USER taoHouChao
 * @DATE 21:13 2025/8/7
 */
@Data
@Schema(description = "老师修改命令")
public class TeacherModifyCmd {

    @Schema(description = "老师id")
    @NotNull(message = "学生id不能为空")
    private Integer id;

    @Schema(description = "姓名")
    @NotEmpty(message = "姓名不能为空")
    private String name;

    @Schema(description = "账号")
    @NotEmpty(message = "账号不能为空")
    private String account;

    @Schema(description = "密码")
    @NotEmpty(message = "密码不能为空")
    private String password;

    @Schema(description = "手机号")
    @NotEmpty(message = "手机号不能为空")
    private String phone;
}
