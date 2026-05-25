package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @USER taoHouChao
 * @DATE 20:54 2025/8/14
 */
@Data
@Schema(description = "学生答辩分页查询参数")
public class StudentDefensePagingQuery {

    @Schema(description = "考核主题")
    private String theme;

    @Schema(description = "页码")
    private Integer page;

    @Schema(description = "每页数量")
    private Integer size;
}
