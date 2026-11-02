package system.assessment.defense.application.dto.interview;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 学生端面试状态。前端完全依据它渲染页面，刷新 / 断网重连后重新拉取即可恢复。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewStateDTO {

    public static final String NOT_STARTED = "NOT_STARTED";
    public static final String DEFENSE = "DEFENSE";
    public static final String QUESTIONS = "QUESTIONS";
    public static final String READY_TO_SUBMIT = "READY_TO_SUBMIT";
    public static final String SUBMITTED = "SUBMITTED";

    private Integer assessmentId;

    private String theme;

    private Boolean defenseEnabled;

    private Boolean questionEnabled;

    private Boolean followUpEnabled;

    @Schema(description = "答辩最长时长（秒）")
    private Integer duration;

    @Schema(description = "每题最长作答时长（秒）")
    private Integer answerTime;

    @Schema(description = "NOT_STARTED / DEFENSE / QUESTIONS / READY_TO_SUBMIT / SUBMITTED")
    private String status;

    private Boolean defenseDone;

    @Schema(description = "本场抽中的主问题数量")
    private Integer total;

    @Schema(description = "已完成（含追问）的主问题数量")
    private Integer answeredCount;

    @Schema(description = "当前需要作答的题目，仅 QUESTIONS 状态非空")
    private CurrentQuestion current;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime serverTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "学生所在场次的开始时间（分场次考试、未开始时）")
    private LocalDateTime sessionStart;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sessionEnd;

    @Schema(description = "暂时不能开始的原因（如未到场次时间），为空表示可以开始")
    private String startBlockedReason;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentQuestion {

        @Schema(description = "题序，从 1 开始")
        private Integer index;

        @Schema(description = "是否为追问")
        private Boolean followUp;

        @Schema(description = "追问序号，从 1 开始；主问题为 0")
        private Integer followIndex;

        @Schema(description = "本题的追问总数")
        private Integer followTotal;

        private String content;
    }
}
