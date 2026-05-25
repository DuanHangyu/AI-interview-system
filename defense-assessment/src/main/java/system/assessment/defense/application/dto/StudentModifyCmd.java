package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import system.assessment.defense.infrastructure.repository.dao.po.UserPO;

/**
 * @USER taoHouChao
 * @DATE 21:13 2025/8/7
 */
@Data
@Schema(description = "学生修改命令")
public class StudentModifyCmd {

    @Schema(description = "学生id")
    @NotNull(message = "学生id不能为空")
    private Integer id;

    @Schema(description = "姓名")
    @NotEmpty(message = "姓名不能为空")
    private String name;

    @Schema(description = "学号")
    @NotEmpty(message = "学号不能为空")
    private String account;

    @Schema(description = "密码")
    @NotEmpty(message = "密码不能为空")
    private String password;

    @Schema(description = "班级")
    @NotEmpty(message = "班级不能为空")
    private String schoolClass;

    public UserPO toPO(){
        return UserPO.builder()
                .id(id)
                .name(name)
                .account(account)
                .password(password)
                .type(0)
                .schoolClass(schoolClass)
                .build();
    }
}
