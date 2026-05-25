package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @USER taoHouChao
 * @DATE 20:40 2025/8/17
 */
@Data
@Schema(description = "学习记录分页查询参数")
public class StudyRecordPagingQuery {

    @Schema(description = "学生姓名")
    private String studentName;

    @Schema(description = "考核主题")
    private String theme;

    @Schema(description = "页码")
    private Integer page;

    @Schema(description = "每页数量")
    private Integer size;
}
