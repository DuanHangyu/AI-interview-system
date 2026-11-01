package system.assessment.defense.application.service.interview;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import system.assessment.defense.application.dto.interview.InterviewStateDTO;
import system.assessment.defense.application.dto.interview.InterviewStateDTO.CurrentQuestion;
import system.assessment.defense.application.task.TaskQueue;
import system.assessment.defense.application.task.TaskTypes;
import system.assessment.defense.infrastructure.common.WavUtils;
import system.assessment.defense.infrastructure.emuns.ErrorCodeEnums;
import system.assessment.defense.infrastructure.exception.BusinessException;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentQuestionPO;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentSettingPO;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentStudentRelationPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentAppointmentPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentQuestionAnswerPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentRecordPO;
import system.assessment.defense.infrastructure.repository.dao.service.AssessmentQuestionService;
import system.assessment.defense.infrastructure.repository.dao.service.AssessmentSettingService;
import system.assessment.defense.infrastructure.repository.dao.service.AssessmentStudentRelationService;
import system.assessment.defense.infrastructure.repository.dao.service.StudentAssessmentAppointmentService;
import system.assessment.defense.infrastructure.repository.dao.service.StudentAssessmentQuestionAnswerService;
import system.assessment.defense.infrastructure.repository.dao.service.StudentAssessmentRecordService;
import system.assessment.defense.infrastructure.storage.FileStorage;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * 学生端面试流程。
 * <p>
 * 面试进度完全保存在数据库中，每个接口都是幂等的：前端断网重试、刷新页面、同时打开两个标签页，
 * 最终都会收敛到同一个状态。接口内只做存储写入和数据库更新，语音转写、评分全部交给后台任务，
 * 请求耗时与大模型无关，高峰期也不会占满 Web 线程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private static final int RECORD_IN_PROGRESS = 0;
    private static final int RECORD_DONE = 1;
    private static final int RECORD_ANALYZING = 2;
    private static final int RECORD_RETAKE = 3;
    private static final int APPOINTMENT_IN_ASSESSMENT = 1;
    private static final int APPOINTMENT_DONE = 2;
    private static final long MAX_AUDIO_BYTES = 80L * 1024 * 1024;
    private static final double MIN_AUDIO_SECONDS = 0.5;

    private final AssessmentSettingService settingService;
    private final AssessmentQuestionService questionService;
    private final AssessmentStudentRelationService relationService;
    private final StudentAssessmentRecordService recordService;
    private final StudentAssessmentQuestionAnswerService answerService;
    private final StudentAssessmentAppointmentService appointmentService;
    private final QuestionBankService questionBankService;
    private final FileStorage fileStorage;
    private final TaskQueue taskQueue;
    private final TransactionTemplate transactionTemplate;
    private final Random random = new SecureRandom();

    public InterviewStateDTO state(Integer studentId, Integer assessmentId) {
        AssessmentSettingPO setting = loadSetting(assessmentId);
        StudentAssessmentRecordPO record = recordService.findByStudentIdAndAssessmentId(studentId, assessmentId).orElse(null);
        return buildState(setting, record, isStarted(record) ? listAnswers(studentId, assessmentId) : List.of());
    }

    /**
     * 开始（或继续）面试。首次开始时为学生抽题并持久化，之后重复调用只会恢复进度。
     */
    public InterviewStateDTO start(Integer studentId, Integer assessmentId) {
        AssessmentSettingPO setting = loadSetting(assessmentId);
        if (!isTrue(setting.getDefense()) && !isTrue(setting.getQuestion())) {
            throw new BusinessException(400, "该考核未配置答辩或问答环节");
        }
        long assigned = relationService.count(Wrappers.lambdaQuery(AssessmentStudentRelationPO.class)
                .eq(AssessmentStudentRelationPO::getAssessmentId, assessmentId)
                .eq(AssessmentStudentRelationPO::getStudentId, studentId));
        if (assigned == 0) {
            throw new BusinessException(403, "你不在本场考核的名单中");
        }
        StudentAssessmentRecordPO record = findOrCreateRecord(studentId, assessmentId);
        if (!isStarted(record)) {
            inTransaction(() -> beginInterview(setting, record, studentId));
        }
        return state(studentId, assessmentId);
    }

    public InterviewStateDTO uploadDefense(Integer studentId, Integer assessmentId, MultipartFile audio) {
        AssessmentSettingPO setting = loadSetting(assessmentId);
        StudentAssessmentRecordPO record = startedRecord(studentId, assessmentId);
        InterviewStateDTO state = buildState(setting, record, listAnswers(studentId, assessmentId));
        if (!InterviewStateDTO.DEFENSE.equals(state.getStatus())) {
            if (isTrue(state.getDefenseDone())) {
                return state;
            }
            throw new BusinessException(409, "当前不在答辩环节，请刷新页面");
        }
        byte[] wav = readAudio(audio);
        int seconds = (int) Math.round(WavUtils.parse(wav).durationSeconds());
        String key = fileStorage.put("defense", "defense.wav", wav, "audio/wav");
        LocalDateTime now = LocalDateTime.now();
        inTransaction(() -> {
            boolean updated = recordService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                    .set(StudentAssessmentRecordPO::getDefenseVoice, key)
                    .set(StudentAssessmentRecordPO::getDefenseTime, seconds)
                    .set(StudentAssessmentRecordPO::getStartDefenseTime, now.minusSeconds(seconds))
                    .set(StudentAssessmentRecordPO::getEndDefenseTime, now)
                    .set(StudentAssessmentRecordPO::getUpdateTime, now)
                    .eq(StudentAssessmentRecordPO::getId, record.getId())
                    .eq(StudentAssessmentRecordPO::getState, RECORD_IN_PROGRESS)
                    .isNull(StudentAssessmentRecordPO::getEndDefenseTime));
            if (updated) {
                taskQueue.enqueue(TaskTypes.ASR_DEFENSE, record.getId());
            }
        });
        return state(studentId, assessmentId);
    }

    public InterviewStateDTO uploadAnswer(Integer studentId, Integer assessmentId, Integer index, boolean followUp,
                                          MultipartFile audio) {
        AssessmentSettingPO setting = loadSetting(assessmentId);
        StudentAssessmentRecordPO record = startedRecord(studentId, assessmentId);
        List<StudentAssessmentQuestionAnswerPO> answers = listAnswers(studentId, assessmentId);
        StudentAssessmentQuestionAnswerPO answer = answers.stream()
                .filter(a -> Objects.equals(a.getSortIndex(), index))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "题目不存在"));
        InterviewStateDTO state = buildState(setting, record, answers);
        boolean alreadyAnswered = followUp ? answer.getFollowAnswerVoice() != null : answer.getAnswerVoice() != null;
        if (alreadyAnswered) {
            // 前端重试导致的重复上传，直接返回最新状态
            return state;
        }
        CurrentQuestion current = state.getCurrent();
        if (current == null || !Objects.equals(current.getIndex(), index) || !Objects.equals(current.getFollowUp(), followUp)) {
            throw new BusinessException(409, "请按顺序作答，请刷新页面后继续");
        }
        byte[] wav = readAudio(audio);
        String key = fileStorage.put("answers", "answer.wav", wav, "audio/wav");
        SFunction<StudentAssessmentQuestionAnswerPO, String> voiceColumn = followUp
                ? StudentAssessmentQuestionAnswerPO::getFollowAnswerVoice : StudentAssessmentQuestionAnswerPO::getAnswerVoice;
        inTransaction(() -> {
            boolean updated = answerService.update(Wrappers.lambdaUpdate(StudentAssessmentQuestionAnswerPO.class)
                    .set(voiceColumn, key)
                    .eq(StudentAssessmentQuestionAnswerPO::getId, answer.getId())
                    .isNull(voiceColumn));
            if (updated) {
                taskQueue.enqueue(followUp ? TaskTypes.ASR_FOLLOW_UP : TaskTypes.ASR_ANSWER, answer.getId());
            }
        });
        return state(studentId, assessmentId);
    }

    public InterviewStateDTO submit(Integer studentId, Integer assessmentId) {
        AssessmentSettingPO setting = loadSetting(assessmentId);
        StudentAssessmentRecordPO record = startedRecord(studentId, assessmentId);
        InterviewStateDTO state = buildState(setting, record, listAnswers(studentId, assessmentId));
        if (InterviewStateDTO.SUBMITTED.equals(state.getStatus())) {
            return state;
        }
        if (!InterviewStateDTO.READY_TO_SUBMIT.equals(state.getStatus())) {
            throw new BusinessException(409, "还有未完成的环节，请刷新页面后继续");
        }
        inTransaction(() -> {
            boolean updated = recordService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                    .set(StudentAssessmentRecordPO::getState, RECORD_ANALYZING)
                    .set(StudentAssessmentRecordPO::getReAnalysis, true)
                    .set(StudentAssessmentRecordPO::getUpdateTime, LocalDateTime.now())
                    .eq(StudentAssessmentRecordPO::getId, record.getId())
                    .eq(StudentAssessmentRecordPO::getState, RECORD_IN_PROGRESS));
            if (updated) {
                appointmentService.update(Wrappers.lambdaUpdate(StudentAssessmentAppointmentPO.class)
                        .set(StudentAssessmentAppointmentPO::getState, APPOINTMENT_DONE)
                        .eq(StudentAssessmentAppointmentPO::getStudentId, studentId)
                        .eq(StudentAssessmentAppointmentPO::getAssessmentId, assessmentId));
                taskQueue.enqueue(TaskTypes.ANALYSIS, record.getId());
            }
        });
        return state(studentId, assessmentId);
    }

    /**
     * 当前题目的播报音频。只允许获取正在作答的那一题，避免提前获取后续题目。
     */
    public byte[] questionAudio(Integer studentId, Integer assessmentId, Integer index, boolean followUp) {
        InterviewStateDTO state = state(studentId, assessmentId);
        CurrentQuestion current = state.getCurrent();
        if (current == null || !Objects.equals(current.getIndex(), index) || !Objects.equals(current.getFollowUp(), followUp)) {
            throw new BusinessException(409, "只能获取当前题目的语音");
        }
        StudentAssessmentQuestionAnswerPO answer = listAnswers(studentId, assessmentId).stream()
                .filter(a -> Objects.equals(a.getSortIndex(), index))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "题目不存在"));
        return questionBankService.questionAudio(answer.getQuestionId(), followUp, current.getContent());
    }

    static InterviewStateDTO buildState(AssessmentSettingPO setting, StudentAssessmentRecordPO record,
                                        List<StudentAssessmentQuestionAnswerPO> answers) {
        boolean defenseEnabled = isTrue(setting.getDefense());
        boolean questionEnabled = isTrue(setting.getQuestion());
        InterviewStateDTO state = InterviewStateDTO.builder()
                .assessmentId(setting.getId())
                .theme(setting.getTheme())
                .defenseEnabled(defenseEnabled)
                .questionEnabled(questionEnabled)
                .followUpEnabled(isTrue(setting.getFollowUp()))
                .duration(setting.getDuration())
                .answerTime(setting.getAnswerTime())
                .defenseDone(record != null && record.getEndDefenseTime() != null && isStarted(record))
                .total(answers.size())
                .answeredCount(0)
                .build();
        if (!isStarted(record)) {
            state.setStatus(InterviewStateDTO.NOT_STARTED);
            return state;
        }
        if (Objects.equals(record.getState(), RECORD_DONE) || Objects.equals(record.getState(), RECORD_ANALYZING)) {
            state.setStatus(InterviewStateDTO.SUBMITTED);
            state.setAnsweredCount(answers.size());
            return state;
        }
        CurrentQuestion current = null;
        int answered = 0;
        for (StudentAssessmentQuestionAnswerPO answer : answers) {
            if (answer.getAnswerVoice() == null) {
                current = new CurrentQuestion(answer.getSortIndex(), false, answer.getQuestion());
                break;
            }
            if (StringUtils.isNotBlank(answer.getFollowQuestion()) && answer.getFollowAnswerVoice() == null) {
                current = new CurrentQuestion(answer.getSortIndex(), true, answer.getFollowQuestion());
                break;
            }
            answered++;
        }
        state.setAnsweredCount(answered);
        if (defenseEnabled && !isTrue(state.getDefenseDone())) {
            state.setStatus(InterviewStateDTO.DEFENSE);
        } else if (questionEnabled && current != null) {
            state.setStatus(InterviewStateDTO.QUESTIONS);
            state.setCurrent(current);
        } else {
            state.setStatus(InterviewStateDTO.READY_TO_SUBMIT);
        }
        return state;
    }

    private void beginInterview(AssessmentSettingPO setting, StudentAssessmentRecordPO record, Integer studentId) {
        LocalDateTime now = LocalDateTime.now();
        // 条件更新充当锁：并发的多个 start 请求只有一个能把记录切换为“已开始”，其余的直接恢复进度
        boolean won = recordService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                .set(StudentAssessmentRecordPO::getStartTime, now)
                .set(StudentAssessmentRecordPO::getState, RECORD_IN_PROGRESS)
                .set(StudentAssessmentRecordPO::getStartDefenseTime, null)
                .set(StudentAssessmentRecordPO::getEndDefenseTime, null)
                .set(StudentAssessmentRecordPO::getDefenseTime, null)
                .set(StudentAssessmentRecordPO::getDefenseVoice, null)
                .set(StudentAssessmentRecordPO::getDefenseContent, null)
                .set(StudentAssessmentRecordPO::getDefenseResult, null)
                .set(StudentAssessmentRecordPO::getScore, -1)
                .set(StudentAssessmentRecordPO::getCheckScore, 0)
                .set(StudentAssessmentRecordPO::getReAnalysis, false)
                .set(StudentAssessmentRecordPO::getUpdateTime, now)
                .eq(StudentAssessmentRecordPO::getId, record.getId())
                .and(w -> w.isNull(StudentAssessmentRecordPO::getStartTime)
                        .or().eq(StudentAssessmentRecordPO::getState, RECORD_RETAKE)));
        if (!won) {
            return;
        }
        answerService.clearQuestionAnswer(studentId, setting.getId());
        if (isTrue(setting.getQuestion())) {
            List<AssessmentQuestionPO> bank = questionService.listByAssessmentId(setting.getId());
            int count = setting.getQuestionCount() == null ? 0 : setting.getQuestionCount();
            List<AssessmentQuestionPO> drawn = QuestionDrawer.draw(bank, count, random);
            if (drawn.isEmpty()) {
                throw new BusinessException(500, "该考核的题库为空，请联系老师");
            }
            boolean askFollowUp = isTrue(setting.getFollowUp());
            for (int i = 0; i < drawn.size(); i++) {
                AssessmentQuestionPO q = drawn.get(i);
                answerService.save(StudentAssessmentQuestionAnswerPO.builder()
                        .assessmentId(setting.getId())
                        .studentId(studentId)
                        .questionId(q.getId())
                        .sortIndex(i + 1)
                        .dimension(q.getDimension())
                        .question(q.getContent())
                        .referenceAnswer(q.getReferenceAnswer())
                        .followQuestion(askFollowUp ? StringUtils.trimToNull(q.getFollowUpQuestion()) : null)
                        .createTime(now)
                        .build());
            }
            log.info("interview started, studentId:{}, assessmentId:{}, drawn:{}/{}", studentId, setting.getId(),
                    drawn.size(), bank.size());
        }
        appointmentService.update(Wrappers.lambdaUpdate(StudentAssessmentAppointmentPO.class)
                .set(StudentAssessmentAppointmentPO::getState, APPOINTMENT_IN_ASSESSMENT)
                .eq(StudentAssessmentAppointmentPO::getStudentId, studentId)
                .eq(StudentAssessmentAppointmentPO::getAssessmentId, setting.getId()));
    }

    /**
     * 在事务中执行；遇到死锁 / 锁等待超时时自动重试（MySQL 官方建议的处理方式），对调用方透明。
     */
    private void inTransaction(Runnable action) {
        for (int attempt = 1; ; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status -> action.run());
                return;
            } catch (PessimisticLockingFailureException e) {
                if (attempt >= 3) {
                    throw e;
                }
                log.warn("transaction lock conflict, retry {}: {}", attempt, e.getMessage());
                try {
                    Thread.sleep(20L * attempt + random.nextInt(30));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private StudentAssessmentRecordPO findOrCreateRecord(Integer studentId, Integer assessmentId) {
        Optional<StudentAssessmentRecordPO> existing = recordService.findByStudentIdAndAssessmentId(studentId, assessmentId);
        if (existing.isPresent()) {
            return existing.get();
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            StudentAssessmentRecordPO record = StudentAssessmentRecordPO.builder()
                    .studentId(studentId)
                    .assessmentId(assessmentId)
                    .state(RECORD_IN_PROGRESS)
                    .score(-1)
                    .checkScore(0)
                    .reAnalysis(false)
                    .createTime(now)
                    .updateTime(now)
                    .build();
            recordService.save(record);
            return record;
        } catch (DuplicateKeyException e) {
            // 并发请求已经创建了记录（student_id + assessment_id 唯一）
            return recordService.findByStudentIdAndAssessmentId(studentId, assessmentId)
                    .orElseThrow(() -> new BusinessException(ErrorCodeEnums.STUDENT_ASSESSMENT_NOT_FOUND));
        }
    }

    private StudentAssessmentRecordPO startedRecord(Integer studentId, Integer assessmentId) {
        StudentAssessmentRecordPO record = recordService.findByStudentIdAndAssessmentId(studentId, assessmentId)
                .orElseThrow(() -> new BusinessException(409, "面试尚未开始，请刷新页面"));
        if (!isStarted(record)) {
            throw new BusinessException(409, "面试尚未开始，请刷新页面");
        }
        return record;
    }

    private List<StudentAssessmentQuestionAnswerPO> listAnswers(Integer studentId, Integer assessmentId) {
        return answerService.listByAssessmentIdAndStudentId(assessmentId, studentId).stream()
                .sorted(Comparator.comparing(StudentAssessmentQuestionAnswerPO::getSortIndex,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private AssessmentSettingPO loadSetting(Integer assessmentId) {
        if (assessmentId == null) {
            throw new BusinessException(400, "缺少考核 id");
        }
        return settingService.findById(assessmentId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnums.ASSESSMENT_SETTING_NOT_FOUND));
    }

    private static byte[] readAudio(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new BusinessException(400, "录音为空，请重新录制");
        }
        if (audio.getSize() > MAX_AUDIO_BYTES) {
            throw new BusinessException(400, "录音文件过大");
        }
        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException e) {
            throw new BusinessException(400, "读取录音失败，请重试");
        }
        try {
            if (WavUtils.parse(bytes).durationSeconds() < MIN_AUDIO_SECONDS) {
                throw new BusinessException(400, "录音时间过短，请重新录制");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "录音格式不正确：" + e.getMessage());
        }
        return bytes;
    }

    private static boolean isStarted(StudentAssessmentRecordPO record) {
        return record != null && record.getStartTime() != null && !Objects.equals(record.getState(), RECORD_RETAKE);
    }

    private static boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }
}
