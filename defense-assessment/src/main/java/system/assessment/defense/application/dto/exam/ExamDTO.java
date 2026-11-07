package system.assessment.defense.application.dto.exam;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 考试场次与考场监控相关的请求 / 响应对象
 */
public final class ExamDTO {

    private static final String TIME = "yyyy-MM-dd HH:mm:ss";

    private ExamDTO() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "考试场次")
    public static class Session {

        @Schema(description = "场次 id，新增时为空")
        private Integer id;

        @JsonFormat(pattern = TIME)
        @Schema(description = "开始时间")
        private LocalDateTime startTime;

        @JsonFormat(pattern = TIME)
        @Schema(description = "结束时间，之后不能再开始面试")
        private LocalDateTime endTime;

        @Schema(description = "地点")
        private String location;

        @Schema(description = "人数上限，为空不限")
        private Integer capacity;

        @Schema(description = "已安排人数（只读）")
        private Integer assignedCount;
    }

    @Data
    @Schema(description = "保存场次")
    public static class SaveSessionsCmd {

        @NotNull
        private Integer assessmentId;

        @Schema(description = "全部场次；未出现的场次会被删除，其中未开考的学生变为未安排")
        private List<Session> sessions;
    }

    @Data
    @Schema(description = "安排学生到场次")
    public static class AssignCmd {

        @NotNull
        private Integer assessmentId;

        @Schema(description = "场次 id；为空表示取消安排")
        private Integer sessionId;

        private List<Integer> studentIds;
    }

    @Data
    @Schema(description = "考场 id")
    public static class AssessmentCmd {

        @NotNull
        private Integer assessmentId;
    }

    @Data
    @Schema(description = "向学生公布或收回成绩报告")
    public static class ShowResultCmd {

        @NotNull
        private Integer assessmentId;

        @NotNull
        private Boolean showResult;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "安排结果")
    public static class AssignResult {

        @Schema(description = "成功安排（或取消）的人数")
        private Integer assigned;

        @Schema(description = "已开考或已完成、未改动的人数")
        private Integer skipped;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "考场监控")
    public static class Monitor {

        private Integer assessmentId;

        private String theme;

        @Schema(description = "是否分场次考试")
        private Boolean bySession;

        private Integer totalScore;

        private Integer passScore;

        @Schema(description = "学生能否查看成绩报告")
        private Boolean showResult;

        @JsonFormat(pattern = TIME)
        private LocalDateTime serverTime;

        private List<Session> sessions;

        @Schema(description = "各状态人数，key 为 ExamStatus 名称")
        private Map<String, Integer> counts;

        private List<StudentRow> students;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "考场中的一名学生")
    public static class StudentRow {

        private Integer studentId;

        private String name;

        private String account;

        private String schoolClass;

        private Integer sessionId;

        @JsonFormat(pattern = TIME)
        private LocalDateTime sessionStart;

        @JsonFormat(pattern = TIME)
        private LocalDateTime sessionEnd;

        private String location;

        @Schema(description = "ExamStatus 名称")
        private String status;

        private String statusLabel;

        @Schema(description = "是否为老师安排的重考")
        private Boolean retake;

        @JsonFormat(pattern = TIME)
        private LocalDateTime startTime;

        @JsonFormat(pattern = TIME)
        @Schema(description = "最后一次提交回答的时间")
        private LocalDateTime lastActiveTime;

        @Schema(description = "当前进度，如“第 1 题 · 追问 2/3”")
        private String progress;

        @Schema(description = "已完成的作答段数（主问题与每个追问各算一段）")
        private Integer answeredSteps;

        private Integer totalSteps;

        private Integer recordId;

        private Integer finalScore;

        private Boolean passed;

        private Boolean reviewed;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "学生的面试列表")
    public static class MyInterviews {

        @JsonFormat(pattern = TIME)
        private LocalDateTime serverTime;

        private List<MyInterview> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "学生的一场面试")
    public static class MyInterview {

        private Integer assessmentId;

        private String theme;

        private Boolean defense;

        private Boolean question;

        private Integer questionCount;

        private Integer answerTime;

        private Integer duration;

        private Integer totalScore;

        private String status;

        private String statusLabel;

        private Boolean retake;

        @JsonFormat(pattern = TIME)
        private LocalDateTime sessionStart;

        @JsonFormat(pattern = TIME)
        private LocalDateTime sessionEnd;

        private String location;

        @Schema(description = "能否进入面试页面（含开场前 10 分钟候考）")
        private Boolean canEnter;

        @Schema(description = "能否开始答题")
        private Boolean canStart;

        @Schema(description = "学生能否查看报告")
        private Boolean showResult;
    }
}
