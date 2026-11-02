package system.assessment.defense.application.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import system.assessment.defense.application.manage.SpeechManager;
import system.assessment.defense.application.service.interview.AssessmentAnalysisService;
import system.assessment.defense.application.service.interview.QuestionBankService;
import system.assessment.defense.infrastructure.common.WavUtils;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentQuestionAnswerPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentRecordPO;
import system.assessment.defense.infrastructure.repository.dao.service.StudentAssessmentQuestionAnswerService;
import system.assessment.defense.infrastructure.repository.dao.service.StudentAssessmentRecordService;
import system.assessment.defense.infrastructure.storage.FileStorage;

/**
 * 面试相关的后台任务。转写任务把结果写回答题记录；评分任务等全部转写完成后执行。
 */
@Configuration
@RequiredArgsConstructor
public class InterviewTaskHandlers {

    static final String NO_ANSWER = "没有回答";
    static final String TRANSCRIBE_FAILED = "（语音转写失败，无法评估）";
    static final String NO_VOICE = "（录音中没有检测到声音）";
    /** 峰值低于约 -30dBFS 视为没有采集到人声（正常说话的峰值远高于此） */
    static final double SILENCE_PEAK = 0.035;

    private final SpeechManager speechManager;
    private final FileStorage fileStorage;
    private final StudentAssessmentRecordService recordService;
    private final StudentAssessmentQuestionAnswerService answerService;
    private final AssessmentAnalysisService analysisService;
    private final QuestionBankService questionBankService;

    @Value("${task.concurrency.asr:3}")
    private int asrConcurrency;

    @Value("${task.concurrency.analysis:4}")
    private int analysisConcurrency;

    @Value("${task.concurrency.tts:2}")
    private int ttsConcurrency;

    @Bean
    public TaskHandler defenseTranscriptionHandler() {
        return new SimpleHandler(TaskTypes.ASR_DEFENSE, asrConcurrency, 1800) {
            @Override
            public void handle(Integer recordId) {
                StudentAssessmentRecordPO record = recordService.getById(recordId);
                if (record == null || StringUtils.isBlank(record.getDefenseVoice()) || record.getDefenseContent() != null) {
                    return;
                }
                byte[] wav = fileStorage.read(record.getDefenseVoice());
                String text = speechManager.transcribe(wav, null);
                saveDefense(recordId, record.getDefenseVoice(), StringUtils.defaultIfBlank(text, emptyText(wav)));
            }

            @Override
            public void onGiveUp(Integer recordId, Exception lastError) {
                StudentAssessmentRecordPO record = recordService.getById(recordId);
                if (record != null && record.getDefenseVoice() != null) {
                    saveDefense(recordId, record.getDefenseVoice(), TRANSCRIBE_FAILED);
                }
            }
        };
    }

    @Bean
    public TaskHandler answerTranscriptionHandler() {
        return answerHandler(TaskTypes.ASR_ANSWER, false);
    }

    @Bean
    public TaskHandler followUpTranscriptionHandler() {
        return answerHandler(TaskTypes.ASR_FOLLOW_UP, true);
    }

    @Bean
    public TaskHandler analysisHandler() {
        return new SimpleHandler(TaskTypes.ANALYSIS, analysisConcurrency, 900) {
            @Override
            public void handle(Integer recordId) {
                analysisService.analyze(recordId);
            }

            @Override
            public void onGiveUp(Integer recordId, Exception lastError) {
                analysisService.markFailed(recordId);
            }
        };
    }

    @Bean
    public TaskHandler questionAudioHandler() {
        return new SimpleHandler(TaskTypes.TTS_QUESTION, ttsConcurrency, 300) {
            @Override
            public void handle(Integer questionId) {
                questionBankService.ensureAudio(questionId);
            }

            @Override
            public int maxAttempts() {
                // 预生成失败不影响面试：学生端会现场合成或使用浏览器朗读
                return 3;
            }
        };
    }

    private TaskHandler answerHandler(String type, boolean followUp) {
        SFunction<StudentAssessmentQuestionAnswerPO, String> voiceColumn = followUp
                ? StudentAssessmentQuestionAnswerPO::getFollowAnswerVoice : StudentAssessmentQuestionAnswerPO::getAnswerVoice;
        SFunction<StudentAssessmentQuestionAnswerPO, String> textColumn = followUp
                ? StudentAssessmentQuestionAnswerPO::getFollowAnswer : StudentAssessmentQuestionAnswerPO::getAnswer;
        return new SimpleHandler(type, asrConcurrency, 900) {
            @Override
            public void handle(Integer answerId) {
                StudentAssessmentQuestionAnswerPO answer = answerService.getById(answerId);
                if (answer == null) {
                    return;
                }
                String voice = followUp ? answer.getFollowAnswerVoice() : answer.getAnswerVoice();
                String existing = followUp ? answer.getFollowAnswer() : answer.getAnswer();
                if (voice == null || existing != null) {
                    return;
                }
                String question = followUp ? answer.getFollowQuestion() : answer.getQuestion();
                save(answerId, voice, transcribeAnswer(fileStorage.read(voice), question));
            }

            @Override
            public void onGiveUp(Integer answerId, Exception lastError) {
                StudentAssessmentQuestionAnswerPO answer = answerService.getById(answerId);
                String voice = answer == null ? null : (followUp ? answer.getFollowAnswerVoice() : answer.getAnswerVoice());
                if (voice != null) {
                    save(answerId, voice, TRANSCRIBE_FAILED);
                }
            }

            private void save(Integer answerId, String voice, String text) {
                // 带上录音 key 条件：重考后同一行被重置时不会写入旧录音的转写结果
                answerService.update(Wrappers.lambdaUpdate(StudentAssessmentQuestionAnswerPO.class)
                        .set(textColumn, text)
                        .eq(StudentAssessmentQuestionAnswerPO::getId, answerId)
                        .eq(voiceColumn, voice));
            }
        };
    }

    /**
     * 转写一道题的回答。题目作为识别上下文能提升专业术语的准确率，但录音几乎无声时模型会把上下文原样输出，
     * 所以静音录音不带上下文，并且丢弃与题目本身重复的识别结果。
     */
    String transcribeAnswer(byte[] wav, String question) {
        boolean silent = isSilent(wav);
        String text = speechManager.transcribe(wav, silent ? null : question);
        if (StringUtils.isBlank(text) || echoesQuestion(text, question)) {
            return silent ? NO_VOICE : NO_ANSWER;
        }
        return text;
    }

    static boolean isSilent(byte[] wav) {
        try {
            return WavUtils.peakLevel(wav) < SILENCE_PEAK;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String emptyText(byte[] wav) {
        return isSilent(wav) ? NO_VOICE : NO_ANSWER;
    }

    /**
     * 识别结果只是把题目复述了一遍（或是题目中较长的一段），视为没有作答；很短的片段（例如只答了一个关键词）保留。
     */
    static boolean echoesQuestion(String text, String question) {
        String t = lettersOnly(text);
        String q = lettersOnly(question);
        return !t.isEmpty() && q.contains(t) && (t.length() >= 10 || t.length() * 2 >= q.length());
    }

    private static String lettersOnly(String text) {
        return text == null ? "" : text.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase();
    }

    private void saveDefense(Integer recordId, String voice, String text) {
        recordService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                .set(StudentAssessmentRecordPO::getDefenseContent, text)
                .eq(StudentAssessmentRecordPO::getId, recordId)
                .eq(StudentAssessmentRecordPO::getDefenseVoice, voice));
    }

    private abstract static class SimpleHandler implements TaskHandler {

        private final String type;
        private final int concurrency;
        private final int leaseSeconds;

        SimpleHandler(String type, int concurrency, int leaseSeconds) {
            this.type = type;
            this.concurrency = Math.max(1, concurrency);
            this.leaseSeconds = leaseSeconds;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public int concurrency() {
            return concurrency;
        }

        @Override
        public int leaseSeconds() {
            return leaseSeconds;
        }
    }
}
