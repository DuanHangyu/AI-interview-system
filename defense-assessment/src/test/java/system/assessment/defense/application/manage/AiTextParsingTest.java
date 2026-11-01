package system.assessment.defense.application.manage;

import org.junit.jupiter.api.Test;
import system.assessment.defense.application.dto.QuestionItemDTO;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiTextParsingTest {

    @Test
    void parsesQuestionDraftAndSkipsBlankItems() {
        List<QuestionItemDTO> items = AiManager.parseQuestionDraft("""
                ```json
                {"questions":[
                  {"dimension":"技术实现","content":" 介绍你的缓存设计 ","referenceAnswer":"命中率","followUpQuestion":"如何保证一致性？"},
                  {"dimension":"空","content":"   "},
                  {"content":"项目最大的难点是什么？","referenceAnswer":["定位过程","方案对比"]}
                ]}
                ```""");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getContent()).isEqualTo("介绍你的缓存设计");
        assertThat(items.get(0).getFollowUpQuestion()).isEqualTo("如何保证一致性？");
        assertThat(items.get(1).getDimension()).isNull();
        assertThat(items.get(1).getReferenceAnswer()).isEqualTo("定位过程\n方案对比");
    }

    @Test
    void stripsMarkdownBeforeSpeech() {
        String speech = SpeechManager.toSpeakableText("""
                ## 问题
                请解释 **CAP 定理**，并说明 `Redis` 的取舍：
                ```java
                int a = 1;
                ```
                - 分区容错""");
        assertThat(speech).doesNotContain("#", "*", "`", "int a");
        assertThat(speech).contains("CAP 定理", "Redis", "代码见屏幕", "分区容错");
    }
}
