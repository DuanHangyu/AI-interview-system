package system.assessment.defense.application.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import system.assessment.defense.application.dto.ValueDTO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentRecordPO;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAssessmentServiceTest {

    @Test
    void normalizesLiveQuestionsToTheExpectedCount() {
        JSONArray rawQuestions = JSONUtil.parseObj("""
                {
                  "questions": [
                    {"questionDimension": "技术实现", "question": " 请说明你的缓存策略？ "},
                    {"questionDimension": "空题", "question": "   "},
                    {"questionDimension": "效果验证", "question": "如何验证最终效果？"}
                  ]
                }
                """).getJSONArray("questions");

        List<StudentAssessmentService.LiveQuestion> questions =
                StudentAssessmentService.normalizeLiveQuestionsForInterview(rawQuestions, 3);

        assertThat(questions).hasSize(3);
        assertThat(questions.get(0).getQuestion()).isEqualTo("请说明你的缓存策略？");
        assertThat(questions.get(1).getQuestion()).isEqualTo("如何验证最终效果？");
        assertThat(questions.get(2).getQuestion()).contains("请结合你的答辩内容");
    }

    @Test
    void resolvesDisplayScoreWhenTeacherScoreIsBlank() {
        List<ValueDTO> values = List.of(
                new ValueDTO("指标A", 10),
                new ValueDTO("指标B", 20)
        );

        assertThat(StudentAssessmentService.resolveDisplayScore(null, values)).isEqualTo(30);
        assertThat(StudentAssessmentService.resolveDisplayScore(0, values)).isEqualTo(30);
        assertThat(StudentAssessmentService.resolveDisplayScore(25, values)).isEqualTo(25);
    }

    @Test
    void rejectsEmptyOrTinyAudioPayloads() {
        ByteArrayOutputStream emptyAudio = new ByteArrayOutputStream();
        ByteArrayOutputStream tinyAudio = new ByteArrayOutputStream();
        tinyAudio.writeBytes(new byte[128]);
        ByteArrayOutputStream validAudio = new ByteArrayOutputStream();
        validAudio.writeBytes(new byte[2048]);

        assertThat(StudentAssessmentService.hasValidAudio(null)).isFalse();
        assertThat(StudentAssessmentService.hasValidAudio(emptyAudio)).isFalse();
        assertThat(StudentAssessmentService.hasValidAudio(tinyAudio)).isFalse();
        assertThat(StudentAssessmentService.hasValidAudio(validAudio)).isTrue();
    }

    @Test
    void retakeRecordMustNotSkipDefenseIntro() {
        StudentAssessmentRecordPO retakeRecord = StudentAssessmentRecordPO.builder()
                .state(3)
                .endDefenseTime(LocalDateTime.now())
                .build();
        StudentAssessmentRecordPO currentRecord = StudentAssessmentRecordPO.builder()
                .state(0)
                .endDefenseTime(LocalDateTime.now())
                .build();
        StudentAssessmentRecordPO legacyCurrentRecord = StudentAssessmentRecordPO.builder()
                .endDefenseTime(LocalDateTime.now())
                .build();
        StudentAssessmentRecordPO doneRecord = StudentAssessmentRecordPO.builder()
                .state(1)
                .endDefenseTime(LocalDateTime.now())
                .build();

        assertThat(StudentAssessmentService.isDefenseFinishedForCurrentTodo(retakeRecord)).isFalse();
        assertThat(StudentAssessmentService.isDefenseFinishedForCurrentTodo(currentRecord)).isTrue();
        assertThat(StudentAssessmentService.isDefenseFinishedForCurrentTodo(legacyCurrentRecord)).isTrue();
        assertThat(StudentAssessmentService.isDefenseFinishedForCurrentTodo(doneRecord)).isFalse();
    }
}
