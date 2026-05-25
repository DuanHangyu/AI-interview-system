package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @USER taoHouChao
 * @DATE 15:35 2025/8/18
 */
@Data
@Schema(description = "项目类型删除命令")
public class ProjectTypeDeleteCmd {

    @Schema(description = "项目类型id")
    private Integer id;
}
