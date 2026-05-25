package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @USER taoHouChao
 * @DATE 06:48 2025/8/14
 */
@Data
@Schema(description = "老师分页查询参数")
public class TeacherPageQuery {

    @Schema(description = "老师姓名")
    private String name;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "账号")
    private String account;

    private Integer page;

    private Integer size;
}
