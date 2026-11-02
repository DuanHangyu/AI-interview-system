package system.assessment.defense.application.service.exam;

import org.junit.jupiter.api.Test;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentAppointmentSettingPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentRecordPO;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ExamStatusTest {

    private static final LocalDateTime NINE = LocalDateTime.of(2026, 6, 10, 9, 0);
    private static final AssessmentAppointmentSettingPO SESSION = AssessmentAppointmentSettingPO.builder()
            .id(1).timePeriod(NINE).endTime(NINE.plusHours(1)).build();

    @Test
    void sessionWindowDecidesWhetherStudentCanStart() {
        assertThat(ExamStatus.of(true, null, null, NINE)).isEqualTo(ExamStatus.UNSCHEDULED);
        assertThat(ExamStatus.of(true, null, SESSION, NINE.minusMinutes(30))).isEqualTo(ExamStatus.WAITING);
        assertThat(ExamStatus.of(true, null, SESSION, NINE)).isEqualTo(ExamStatus.OPEN);
        // 迟到不惩罚：场次结束前都可以开始
        assertThat(ExamStatus.of(true, null, SESSION, NINE.plusMinutes(59))).isEqualTo(ExamStatus.OPEN);
        assertThat(ExamStatus.of(true, null, SESSION, NINE.plusMinutes(61))).isEqualTo(ExamStatus.MISSED);
        assertThat(ExamStatus.of(false, null, null, NINE)).isEqualTo(ExamStatus.READY);
    }

    @Test
    void startedInterviewCanAlwaysContinue() {
        StudentAssessmentRecordPO started = StudentAssessmentRecordPO.builder().state(0).startTime(NINE).build();
        ExamStatus status = ExamStatus.of(true, started, SESSION, NINE.plusHours(3));
        assertThat(status).isEqualTo(ExamStatus.IN_PROGRESS);
        assertThat(status.canStart()).isTrue();
    }

    @Test
    void recordStatesAndRetake() {
        assertThat(ExamStatus.of(true, StudentAssessmentRecordPO.builder().state(1).build(), SESSION, NINE))
                .isEqualTo(ExamStatus.DONE);
        assertThat(ExamStatus.of(true, StudentAssessmentRecordPO.builder().state(2).build(), SESSION, NINE))
                .isEqualTo(ExamStatus.ANALYZING);
        StudentAssessmentRecordPO retake = StudentAssessmentRecordPO.builder().state(3).startTime(NINE).build();
        assertThat(ExamStatus.isRetake(retake)).isTrue();
        assertThat(ExamStatus.of(true, retake, SESSION, NINE.plusMinutes(5))).isEqualTo(ExamStatus.OPEN);
        // 未开始的记录（例如只上传过材料）按场次判断
        StudentAssessmentRecordPO notStarted = StudentAssessmentRecordPO.builder().state(0).build();
        assertThat(ExamStatus.of(true, notStarted, SESSION, NINE.minusMinutes(1))).isEqualTo(ExamStatus.WAITING);
    }

    @Test
    void earlyEntryOnlyTenMinutesBeforeStart() {
        assertThat(ExamStatus.WAITING.canEnter(SESSION, NINE.minusMinutes(11))).isFalse();
        assertThat(ExamStatus.WAITING.canEnter(SESSION, NINE.minusMinutes(10))).isTrue();
        assertThat(ExamStatus.WAITING.canStart()).isFalse();
        assertThat(ExamStatus.MISSED.canEnter(SESSION, NINE.plusHours(2))).isFalse();
    }

    @Test
    void sessionLabel() {
        assertThat(ExamSessionService.sessionLabel(SESSION)).isEqualTo("6月10日 09:00–10:00");
        assertThat(ExamSessionService.sessionLabel(null)).isEmpty();
    }
}
