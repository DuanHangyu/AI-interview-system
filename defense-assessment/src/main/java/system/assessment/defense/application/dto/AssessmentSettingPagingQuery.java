package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @USER taoHouChao
 * @DATE 18:30 2025/8/12
 */
@Data
@Schema(description = "考核设置分页查询参数")
public class AssessmentSettingPagingQuery {

    @Schema(description = "考核主题")
    private String theme;

    @Schema(description = "页码")
    private Integer page;

    @Schema(description = "每页数量")
    private Integer size;
}
