package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @USER taoHouChao
 * @DATE 15:03 2025/8/22
 */
@Data
@Schema(description = "学生考核详情")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StudentAssessmentDetailDTO {

    @Schema(description = "考核记录id")
    private Integer id;

    @Schema(description = "学生姓名")
    private String studentName;

    @Schema(description = "考核得分")
    private Integer score;

    @Schema(description = "考核总分")
    private Integer totalScore;

    @Schema(description = "通过分数")
    private Integer passScore;

    @Schema(description = "考核问答结果")
    private List<AnswerEvaluationDTO> result;

    @Schema(description = "考核指标结果")
    private List<ValueDTO> value;

    @Schema(description = "答辩分析结果")
    private DefenseDTO defense;

    @Schema(description = "分析结果")
    private AnalysisBO analysis;

    @Schema(description = "总结")
    private String summary;

    @Schema(description = "是否显示结果")
    private Boolean showResult;

    @Schema(description = "考核记录状态 0考核中 1完成 2分析中")
    private Integer state;

    @Schema(description = "是否正在重新分析")
    private Boolean reAnalysis;

    @Schema(description = "地点")
    private String location;
    @Schema(description = "面试名称")
    private String theme;
    @Schema(description = "学号")
    private String studentAccount;
    @Schema(description = "班级")
    private String schoolClass;
    @Schema(description = "面试时间")
    private LocalDateTime interviewTime;
    @Schema(description = "是否包含答辩环节")
    private Boolean defenseEnabled;
    @Schema(description = "是否包含问答环节")
    private Boolean questionEnabled;
    @Schema(description = "AI 评分（各维度 AI 分数之和）")
    private Integer aiScore;
    @Schema(description = "评分维度（含 AI 分、复核后分数、满分）")
    private List<ReportDimensionDTO> dimensions;
    @Schema(description = "教师复核信息，未复核为空")
    private TeacherReviewBO review;
}
