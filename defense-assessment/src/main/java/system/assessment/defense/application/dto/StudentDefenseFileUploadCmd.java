package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @USER taoHouChao
 * @DATE 10:54 2025/8/15
 */
@Data
@Schema(description = "学生考核文件上传命令")
public class StudentDefenseFileUploadCmd {

    @Schema(description = "考核ID")
    private Integer assessmentId;

    @Schema(description = "文件")
    private FileDTO file;
}
