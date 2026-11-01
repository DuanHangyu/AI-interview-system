package system.assessment.defense.application.service.interview;

import org.junit.jupiter.api.Test;
import system.assessment.defense.application.dto.QuestionItemDTO;
import system.assessment.defense.application.dto.interview.InterviewStateDTO;
import system.assessment.defense.infrastructure.exception.BusinessException;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentSettingPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentQuestionAnswerPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentRecordPO;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewStateTest {

    private static final LocalDateTime NOW = LocalDateTime.now();

    @Test
    void notStartedWithoutRecordOrStartTimeOrWhenRetaking() {
        AssessmentSettingPO setting = setting(true, true, false);
        assertThat(state(setting, null).getStatus()).isEqualTo(InterviewStateDTO.NOT_STARTED);
        assertThat(state(setting, record(0, null, null)).getStatus()).isEqualTo(InterviewStateDTO.NOT_STARTED);
        assertThat(state(setting, record(3, NOW, NOW)).getStatus()).isEqualTo(InterviewStateDTO.NOT_STARTED);
    }

    @Test
    void defenseComesFirstWhenEnabled() {
        AssessmentSettingPO setting = setting(true, true, false);
        InterviewStateDTO state = state(setting, record(0, NOW, null), answer(1, "Q1", null, null, null));
        assertThat(state.getStatus()).isEqualTo(InterviewStateDTO.DEFENSE);
        assertThat(state.getCurrent()).isNull();
    }

    @Test
    void walksQuestionsAndFollowUpsInOrder() {
        AssessmentSettingPO setting = setting(false, true, true);
        StudentAssessmentRecordPO record = record(0, NOW, null);

        InterviewStateDTO first = state(setting, record,
                answer(1, "Q1", null, "F1", null), answer(2, "Q2", null, null, null));
        assertThat(first.getStatus()).isEqualTo(InterviewStateDTO.QUESTIONS);
        assertThat(first.getCurrent().getIndex()).isEqualTo(1);
        assertThat(first.getCurrent().getFollowUp()).isFalse();
        assertThat(first.getCurrent().getContent()).isEqualTo("Q1");

        InterviewStateDTO followUp = state(setting, record,
                answer(1, "Q1", "a.wav", "F1", null), answer(2, "Q2", null, null, null));
        assertThat(followUp.getCurrent().getIndex()).isEqualTo(1);
        assertThat(followUp.getCurrent().getFollowUp()).isTrue();
        assertThat(followUp.getCurrent().getContent()).isEqualTo("F1");
        assertThat(followUp.getAnsweredCount()).isZero();

        InterviewStateDTO second = state(setting, record,
                answer(1, "Q1", "a.wav", "F1", "f.wav"), answer(2, "Q2", null, null, null));
        assertThat(second.getCurrent().getIndex()).isEqualTo(2);
        assertThat(second.getAnsweredCount()).isEqualTo(1);
        assertThat(second.getTotal()).isEqualTo(2);

        InterviewStateDTO done = state(setting, record,
                answer(1, "Q1", "a.wav", "F1", "f.wav"), answer(2, "Q2", "b.wav", null, null));
        assertThat(done.getStatus()).isEqualTo(InterviewStateDTO.READY_TO_SUBMIT);
        assertThat(done.getCurrent()).isNull();
        assertThat(done.getAnsweredCount()).isEqualTo(2);
    }

    @Test
    void defenseOnlyAssessmentIsReadyAfterDefense() {
        AssessmentSettingPO setting = setting(true, false, false);
        assertThat(state(setting, record(0, NOW, NOW)).getStatus()).isEqualTo(InterviewStateDTO.READY_TO_SUBMIT);
    }

    @Test
    void submittedWhileAnalyzingOrDone() {
        AssessmentSettingPO setting = setting(true, true, false);
        assertThat(state(setting, record(2, NOW, NOW)).getStatus()).isEqualTo(InterviewStateDTO.SUBMITTED);
        assertThat(state(setting, record(1, NOW, NOW)).getStatus()).isEqualTo(InterviewStateDTO.SUBMITTED);
    }

    @Test
    void validatesQuestionBank() {
        QuestionItemDTO q = QuestionItemDTO.builder().content("题目").build();
        QuestionBankService.validate(false, null, null);
        QuestionBankService.validate(true, 2, List.of(q, q));
        assertThatThrownBy(() -> QuestionBankService.validate(true, 3, List.of(q, q)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("少于");
        assertThatThrownBy(() -> QuestionBankService.validate(true, 1, List.of(QuestionItemDTO.builder().content(" ").build())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能为空");
        assertThatThrownBy(() -> QuestionBankService.validate(true, 0, List.of(q)))
                .isInstanceOf(BusinessException.class);
    }

    private static InterviewStateDTO state(AssessmentSettingPO setting, StudentAssessmentRecordPO record,
                                           StudentAssessmentQuestionAnswerPO... answers) {
        return InterviewService.buildState(setting, record, List.of(answers));
    }

    private static AssessmentSettingPO setting(boolean defense, boolean question, boolean followUp) {
        return AssessmentSettingPO.builder().id(7).theme("主题").defense(defense).question(question)
                .followUp(followUp).duration(60).answerTime(30).questionCount(2).build();
    }

    private static StudentAssessmentRecordPO record(int state, LocalDateTime start, LocalDateTime endDefense) {
        return StudentAssessmentRecordPO.builder().id(1).state(state).startTime(start).endDefenseTime(endDefense).build();
    }

    private static StudentAssessmentQuestionAnswerPO answer(int index, String question, String voice,
                                                            String followUp, String followVoice) {
        return StudentAssessmentQuestionAnswerPO.builder().id(index).sortIndex(index).question(question)
                .answerVoice(voice).followQuestion(followUp).followAnswerVoice(followVoice).build();
    }
}
