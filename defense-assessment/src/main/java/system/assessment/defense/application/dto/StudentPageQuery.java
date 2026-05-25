package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @USER taoHouChao
 * @DATE 14:43 2025/8/9
 */
@Data
public class StudentPageQuery {

    @Schema(description = "学生姓名")
    private String name;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "账号")
    private String account;

    @Schema(description = "班级")
    private String schoolClass;

    private Integer page;

    private Integer size;
}
