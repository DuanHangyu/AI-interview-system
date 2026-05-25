package system.assessment.defense.application.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiManagerFollowUpTest {

    @Test
    void generateFollowUpDirectlyWithoutDecisionSchema() {
        OpenAiManager openAiManager = mock(OpenAiManager.class);
        when(openAiManager.getTextModel()).thenReturn("qwen-text");
        when(openAiManager.chatCompletion(eq("qwen-text"), anyString(), anyString()))
                .thenReturn("请进一步说明你的实验验证数据。");
        GeminiManager geminiManager = new GeminiManager(openAiManager, new ObjectMapper());

        String followUp = geminiManager.generateFollowUp(
                "请说明系统架构设计。",
                "我的系统比较完整。",
                "回答笼统时追问实现细节");

        assertThat(followUp).isEqualTo("请进一步说明你的实验验证数据。");
        verify(openAiManager).chatCompletion(
                eq("qwen-text"),
                anyString(),
                argThat(prompt -> prompt.contains("请直接生成一个追问问题")));
        verify(openAiManager, never()).chatCompletionWithSchema(any(), any(), any(), any(), any());
    }
}
