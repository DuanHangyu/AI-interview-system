package system.assessment.defense.application.task;

public final class TaskTypes {

    /**
     * 转写答辩录音，bizId = student_assessment_record.id
     */
    public static final String ASR_DEFENSE = "ASR_DEFENSE";

    /**
     * 转写主问题回答，bizId = student_assessment_question_answer.id
     */
    public static final String ASR_ANSWER = "ASR_ANSWER";

    /**
     * 转写追问回答，bizId = student_assessment_question_answer.id
     */
    public static final String ASR_FOLLOW_UP = "ASR_FOLLOW_UP";

    /**
     * 面试整体评分，bizId = student_assessment_record.id
     */
    public static final String ANALYSIS = "ANALYSIS";

    /**
     * 预生成题目播报音频，bizId = assessment_question.id
     */
    public static final String TTS_QUESTION = "TTS_QUESTION";

    private TaskTypes() {
    }
}
