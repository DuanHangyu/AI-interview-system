package system.assessment.defense.application.service.exam;

import system.assessment.defense.infrastructure.repository.dao.po.AssessmentAppointmentSettingPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentRecordPO;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 学生在一场面试中的状态，按考核记录和所在场次实时计算（不落库，不会出现定时任务漏改、误改）。
 */
public enum ExamStatus {

    /** 分场次考试，但老师还没有给该学生安排场次 */
    UNSCHEDULED("未安排场次"),
    /** 已安排场次，场次还没开始 */
    WAITING("待考"),
    /** 场次进行中，可以开始面试 */
    OPEN("可开考"),
    /** 不分场次，随时可以开始 */
    READY("可开考"),
    /** 场次已结束仍未开始 */
    MISSED("缺考"),
    IN_PROGRESS("考试中"),
    ANALYZING("评分中"),
    DONE("已完成");

    /** 场次开始前多少分钟可以进入候考页（检查设备），但不能开始答题 */
    public static final int EARLY_ENTRY_MINUTES = 10;

    private static final int RECORD_IN_PROGRESS = 0;
    private static final int RECORD_DONE = 1;
    private static final int RECORD_ANALYZING = 2;
    private static final int RECORD_RETAKE = 3;

    private final String label;

    ExamStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * @param bySession 是否分场次考试
     * @param session   学生所在场次，未安排为空
     */
    public static ExamStatus of(boolean bySession, StudentAssessmentRecordPO record,
                                AssessmentAppointmentSettingPO session, LocalDateTime now) {
        if (record != null) {
            if (Objects.equals(record.getState(), RECORD_DONE)) {
                return DONE;
            }
            if (Objects.equals(record.getState(), RECORD_ANALYZING)) {
                return ANALYZING;
            }
            if (Objects.equals(record.getState(), RECORD_IN_PROGRESS) && record.getStartTime() != null) {
                return IN_PROGRESS;
            }
        }
        if (!bySession) {
            return READY;
        }
        if (session == null || session.getTimePeriod() == null) {
            return UNSCHEDULED;
        }
        if (now.isBefore(session.getTimePeriod())) {
            return WAITING;
        }
        if (session.getEndTime() != null && now.isAfter(session.getEndTime())) {
            return MISSED;
        }
        return OPEN;
    }

    public static boolean isRetake(StudentAssessmentRecordPO record) {
        return record != null && Objects.equals(record.getState(), RECORD_RETAKE);
    }

    /**
     * 能否开始（或继续）答题
     */
    public boolean canStart() {
        return this == OPEN || this == READY || this == IN_PROGRESS;
    }

    /**
     * 能否进入面试页面：可以答题，或距场次开始不到 {@link #EARLY_ENTRY_MINUTES} 分钟（进入候考页检查设备）
     */
    public boolean canEnter(AssessmentAppointmentSettingPO session, LocalDateTime now) {
        if (canStart()) {
            return true;
        }
        return this == WAITING && session != null
                && !now.isBefore(session.getTimePeriod().minusMinutes(EARLY_ENTRY_MINUTES));
    }
}
