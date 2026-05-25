package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * @USER taoHouChao
 * @DATE 15:33 2025/8/18
 */
@Data
@Schema(description = "项目类型创建命令")
public class ProjectTypeCreateCmd {

    @Schema(description = "项目类型")
    private List<String> projectTypes;
}
