package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 教师复核结果，以 JSON 存在考核记录的 teacher_review 字段。AI 的原始评分保持不变，报告中两者对照展示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherReviewBO {

    private List<DimensionScore> dimensions;

    private String comment;

    private Integer reviewerId;

    private String reviewerName;

    /** yyyy-MM-dd HH:mm:ss */
    private String reviewTime;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DimensionScore {

        @Schema(description = "维度名称")
        private String name;

        @Schema(description = "分数")
        private Integer score;
    }
}
