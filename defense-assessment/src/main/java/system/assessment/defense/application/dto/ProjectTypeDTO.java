package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @USER taoHouChao
 * @DATE 15:26 2025/8/18
 */
@Data
@Schema(description = "项目类型")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProjectTypeDTO {

    @Schema(description = "项目类型id")
    private Integer id;

    @Schema(description = "项目类型key")
    private String projectTypeKey;

    @Schema(description = "项目类型")
    private String projectType;
}
