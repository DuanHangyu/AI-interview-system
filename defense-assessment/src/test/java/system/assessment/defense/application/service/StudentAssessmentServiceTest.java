package system.assessment.defense.application.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import system.assessment.defense.application.dto.ValueDTO;

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
}
