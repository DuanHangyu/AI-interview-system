package system.assessment.defense.application.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewTaskHandlersTest {

    @Test
    void detectsTranscriptThatOnlyRepeatsTheQuestion() {
        String question = "请介绍一下你在项目中是如何设计**缓存**的？";
        assertThat(InterviewTaskHandlers.echoesQuestion("请介绍一下你在项目中是如何设计缓存的？", question)).isTrue();
        assertThat(InterviewTaskHandlers.echoesQuestion("你在项目中是如何设计缓存的", question)).isTrue();
        // 只答了一个关键词，不算复述
        assertThat(InterviewTaskHandlers.echoesQuestion("缓存", question)).isFalse();
        assertThat(InterviewTaskHandlers.echoesQuestion("我们用 Redis 做旁路缓存，过期时间 30 分钟", question)).isFalse();
        assertThat(InterviewTaskHandlers.echoesQuestion("", question)).isFalse();
    }
}
