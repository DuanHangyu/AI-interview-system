package system.assessment.defense.application.dto.backed;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @USER taoHouChao
 * @DATE 10:17 2025/9/28
 */
@Data
@Schema(description = "教师修改密码")
public class TeacherModifyPasswordCmd {

    @Schema(description = "旧密码")
    private String oldPassword;

    @Schema(description = "新密码")
    private String newPassword;
}
