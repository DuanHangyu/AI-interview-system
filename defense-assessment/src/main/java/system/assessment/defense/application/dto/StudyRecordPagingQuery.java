package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 面试记录查询条件。列表、批量导出成绩、批量打印报告共用同一组筛选条件。
 */
@Data
@Schema(description = "面试记录查询参数")
public class StudyRecordPagingQuery {

    @Schema(description = "学生姓名或学号（模糊）")
    private String studentName;

    @Schema(description = "面试名称（模糊）")
    private String theme;

    @Schema(description = "面试 id")
    private Integer assessmentId;

    @Schema(description = "场次 id（需同时指定面试）")
    private Integer sessionId;

    @Schema(description = "班级")
    private String schoolClass;

    @Schema(description = "最低分（含）")
    private Integer minScore;

    @Schema(description = "最高分（含）")
    private Integer maxScore;

    @Schema(description = "是否及格")
    private Boolean passed;

    @Schema(description = "是否已复核")
    private Boolean reviewed;

    @Schema(description = "状态：1 已完成，2 评分中；为空表示两者")
    private Integer state;

    @Schema(description = "排序字段：score / time（默认 time）")
    private String sortField;

    @Schema(description = "排序方向：asc / desc（默认 desc）")
    private String sortOrder;

    @Schema(description = "只导出这些记录（导出时使用，优先于筛选条件）")
    private List<Integer> recordIds;

    @Schema(description = "页码")
    private Integer page;

    @Schema(description = "每页数量")
    private Integer size;
}
