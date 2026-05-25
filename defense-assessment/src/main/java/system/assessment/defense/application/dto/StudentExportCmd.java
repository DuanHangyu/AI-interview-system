package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * @USER taoHouChao
 * @DATE 14:36 2025/8/9
 */
@Data
@Schema(description = "学生导出命令")
public class StudentExportCmd {

    @Schema(description = "学生id集合")
    private List<Integer> ids;

    @Schema(description = "导出所有")
    private Boolean all;
}
