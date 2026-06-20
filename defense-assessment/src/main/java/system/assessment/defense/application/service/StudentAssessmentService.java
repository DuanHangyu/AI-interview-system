package system.assessment.defense.application.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import kotlin.Pair;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import system.assessment.defense.application.dto.*;
import system.assessment.defense.application.dto.backed.DigitalHumanDTO;
import system.assessment.defense.application.dto.front.StudentAppointmentCmd;
import system.assessment.defense.application.manage.GeminiManager;
import system.assessment.defense.application.manage.OssManager;
import system.assessment.defense.application.manage.RealtimeSpeechManager;
import system.assessment.defense.application.manage.TtsManager;
import system.assessment.defense.application.manage.WebsocketManager;
import system.assessment.defense.domain.entity.StudentSummary;
import system.assessment.defense.infrastructure.common.*;
import system.assessment.defense.infrastructure.emuns.ErrorCodeEnums;
import system.assessment.defense.infrastructure.exception.BusinessException;
import system.assessment.defense.infrastructure.repository.dao.po.*;
import system.assessment.defense.infrastructure.repository.dao.service.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @USER taoHouChao
 * @DATE 20:09 2025/8/14
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentAssessmentService {

    private static final Map<Integer, ByteArrayOutputStream> VOICE_MAP = new ConcurrentHashMap<>();

    private static final int MIN_VALID_AUDIO_BYTES = 2048;
    private static final int APPOINTMENT_CONFIRMED = 0;
    private static final int APPOINTMENT_IN_ASSESSMENT = 1;
    private static final int APPOINTMENT_MISSED = 3;

    // 现场生成的题目缓存 key: studentId:assessmentId
    private static final Map<String, List<LiveQuestion>> LIVE_QUESTION_MAP = new ConcurrentHashMap<>();
    // 追问缓存 key: studentId:assessmentId:questionIndex
    private static final Map<String, String> FOLLOW_UP_MAP = new ConcurrentHashMap<>();

    private static final long INTERVIEW_SESSION_CACHE_HOURS = 3;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LiveQuestion {
        private String questionDimension;
        private String question;
        private boolean followGenerated;
        private String followQuestion;
    }

    static List<LiveQuestion> normalizeLiveQuestionsForInterview(cn.hutool.json.JSONArray questions, Integer expectedCount) {
        int targetCount = expectedCount == null ? 0 : Math.max(expectedCount, 0);
        List<LiveQuestion> normalized = new ArrayList<>();
        if (questions != null) {
            for (int i = 0; i < questions.size(); i++) {
                if (targetCount > 0 && normalized.size() >= targetCount) {
                    break;
                }
                cn.hutool.json.JSONObject q = questions.getJSONObject(i);
                if (q == null) {
                    continue;
                }
                String question = StringUtils.trimToEmpty(q.getStr("question"));
                if (StringUtils.isBlank(question)) {
                    continue;
                }
                LiveQuestion lq = new LiveQuestion();
                lq.setQuestionDimension(StringUtils.defaultIfBlank(StringUtils.trimToEmpty(q.getStr("questionDimension")), "综合能力"));
                lq.setQuestion(question);
                lq.setFollowGenerated(false);
                normalized.add(lq);
            }
        }

        if (targetCount <= 0) {
            return normalized;
        }
        while (normalized.size() < targetCount) {
            normalized.add(buildFallbackLiveQuestion(normalized.size() + 1));
        }
        return normalized;
    }

    static int resolveDisplayScore(Integer checkScore, List<ValueDTO> value) {
        if (checkScore != null && checkScore > 0) {
            return checkScore;
        }
        if (CollectionUtils.isEmpty(value)) {
            return 0;
        }
        return value.stream()
                .filter(Objects::nonNull)
                .map(ValueDTO::getValue)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    static boolean hasValidAudio(ByteArrayOutputStream byteArrayOutputStream) {
        return byteArrayOutputStream != null && byteArrayOutputStream.size() >= MIN_VALID_AUDIO_BYTES;
    }

    static boolean isDefenseFinishedForCurrentTodo(StudentAssessmentRecordPO record) {
        if (record == null || record.getEndDefenseTime() == null) {
            return false;
        }
        Integer state = record.getState();
        return state == null || Objects.equals(state, 0);
    }

    private static LiveQuestion buildFallbackLiveQuestion(int index) {
        LiveQuestion fallback = new LiveQuestion();
        fallback.setQuestionDimension("综合能力");
        fallback.setQuestion("请结合你的答辩内容，补充说明第%s个关键技术点的依据、实现细节和验证结果。".formatted(index));
        fallback.setFollowGenerated(false);
        return fallback;
    }

    private final RedisTemplate<String, Object> redisTemplate;

    private final DigitalHumanService digitalHumanService;

    private final StudentAssessmentRecordService recordService;

    private final UserService userService;

    private final AssessmentSettingService settingService;

    private final AssessmentAppointmentSettingService appointmentSettingService;

    private final HttpUtils httpUtils;

    private final OssManager ossManger;

    private final GeminiManager geminiManager;

    private final AssessmentMaterialService assessmentMaterialService;

    private final RealtimeSpeechManager realtimeSpeechManager;

    private final TtsManager ttsManager;

    private final ThreadPoolExecutor threadPoolExecutor;

    private final StudentAssessmentQuestionAnswerService questionAnswerService;

    private final StudentAssessmentAppointmentService studentAppointmentService;


    @Value("${scheduler.start:stop}")
    private String runScheduler;

    @Scheduled(cron = "0 0/1 * * * ?")
    public void schedulerCheckStudentAppointment() {
        if (!Objects.equals(runScheduler, "start")) {
            log.info("schedulerCheckStudentAppointment不启动定时任务");
            return;
        }

        log.info("schedulerCheckStudentAppointment开始执行");
        // 预约了，但是没有考试的，需要更新状态
        List<StudentAssessmentAppointmentPO> notAssessList = studentAppointmentService.list(Wrappers.lambdaQuery(StudentAssessmentAppointmentPO.class)
                .in(StudentAssessmentAppointmentPO::getState, 0, 1));
        if (CollectionUtils.isNotEmpty(notAssessList)) {
            for (StudentAssessmentAppointmentPO assessmentAppointmentPO : notAssessList) {
                LocalDateTime timePeriod = assessmentAppointmentPO.getTimePeriod();
                // 如果timePeriod+10分钟小于当前时间，则更新为预约未考核
                if (timePeriod.plusMinutes(10).isBefore(LocalDateTime.now())) {
                    log.info("更新预约未考核, assessmentAppointmentPO:{}", assessmentAppointmentPO);
                    assessmentAppointmentPO.setState(3);
                    assessmentAppointmentPO.setPunishState(0);
                    studentAppointmentService.updateById(assessmentAppointmentPO);
                }
            }
        }
    }

    @NotNull
    private static String getLockKey() {
        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        // 定义格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        // 格式化为字符串
        return now.format(formatter);
    }

    private String liveQuestionsKey(Integer studentId, Integer assessmentId) {
        return RedisConstants.STUDENT_LIVE_QUESTIONS.formatted(studentId, assessmentId);
    }

    private String followUpKey(Integer studentId, Integer assessmentId, Integer questionIndex) {
        return RedisConstants.STUDENT_FOLLOW_UP.formatted(studentId, assessmentId, questionIndex);
    }

    private void cacheLiveQuestions(String mapKey, Integer studentId, Integer assessmentId, List<LiveQuestion> liveQuestions) {
        LIVE_QUESTION_MAP.put(mapKey, liveQuestions);
        redisTemplate.opsForValue().set(liveQuestionsKey(studentId, assessmentId),
                JSONUtil.toJsonStr(liveQuestions), INTERVIEW_SESSION_CACHE_HOURS, TimeUnit.HOURS);
    }

    private List<LiveQuestion> getCachedLiveQuestions(String mapKey, Integer studentId, Integer assessmentId, Integer questionCount) {
        List<LiveQuestion> liveQuestions = LIVE_QUESTION_MAP.get(mapKey);
        if (CollectionUtils.isEmpty(liveQuestions)) {
            Object cached = redisTemplate.opsForValue().get(liveQuestionsKey(studentId, assessmentId));
            if (cached != null && StringUtils.isNotBlank(cached.toString())) {
                liveQuestions = JSONUtil.toList(JSONUtil.parseArray(cached.toString()), LiveQuestion.class);
                if (CollectionUtils.isNotEmpty(liveQuestions)) {
                    LIVE_QUESTION_MAP.put(mapKey, liveQuestions);
                }
            }
        }
        if (questionCount != null && questionCount > 0 && CollectionUtils.isNotEmpty(liveQuestions)
                && liveQuestions.size() != questionCount) {
            cn.hutool.json.JSONArray questions = JSONUtil.parseArray(JSONUtil.toJsonStr(liveQuestions));
            liveQuestions = normalizeLiveQuestionsForInterview(questions, questionCount);
            cacheLiveQuestions(mapKey, studentId, assessmentId, liveQuestions);
        }
        return liveQuestions;
    }

    private void cacheFollowUp(String mapKey, Integer studentId, Integer assessmentId, Integer questionIndex, String followUp) {
        FOLLOW_UP_MAP.put(mapKey, followUp);
        redisTemplate.opsForValue().set(followUpKey(studentId, assessmentId, questionIndex),
                followUp, INTERVIEW_SESSION_CACHE_HOURS, TimeUnit.HOURS);
    }

    private String getCachedFollowUp(String mapKey, Integer studentId, Integer assessmentId, Integer questionIndex) {
        String followUp = FOLLOW_UP_MAP.get(mapKey);
        if (StringUtils.isBlank(followUp)) {
            Object cached = redisTemplate.opsForValue().get(followUpKey(studentId, assessmentId, questionIndex));
            if (cached != null && StringUtils.isNotBlank(cached.toString())) {
                followUp = cached.toString();
                FOLLOW_UP_MAP.put(mapKey, followUp);
            }
        }
        return followUp;
    }

    private void clearInterviewQuestionState(Integer studentId, Integer assessmentId, Integer questionCount) {
        String mapKey = studentId + ":" + assessmentId;
        LIVE_QUESTION_MAP.remove(mapKey);
        FOLLOW_UP_MAP.keySet().removeIf(key -> key.startsWith(mapKey));
        redisTemplate.delete(liveQuestionsKey(studentId, assessmentId));
        redisTemplate.delete(RedisConstants.STUDENT_QUESTION.formatted(studentId, assessmentId));
        if (questionCount != null && questionCount > 0) {
            for (int i = 1; i <= questionCount; i++) {
                redisTemplate.delete(followUpKey(studentId, assessmentId, i));
            }
        }
    }

    /**
     * 定时补偿考核解析失败的考核
     */
    @Scheduled(cron = "0 0/3 * * * ?")
    public void schedulerCheckAnalysis() {
        if (!Objects.equals(runScheduler, "start")) {
            log.info("schedulerCheckAnalysis不启动定时任务");
            return;
        }

        log.info("schedulerCheckAnalysis开始执行");
        // FIX-D: 兜底两类卡死——(1) 正常失败 reAnalysis=false；(2) JVM 在 endAssessment 中途崩溃等
        // 导致 reAnalysis=true 但已陈旧的记录（6 分钟 = 2× cron 周期，确保当前在途行不会被下一轮误抓）。
        List<StudentAssessmentRecordPO> analysisRecords = recordService.list(Wrappers.lambdaQuery(StudentAssessmentRecordPO.class)
                .eq(StudentAssessmentRecordPO::getState, 2)
                .and(wrapper -> wrapper
                        .eq(StudentAssessmentRecordPO::getReAnalysis, false)
                        .or()
                        .lt(StudentAssessmentRecordPO::getUpdateTime, LocalDateTime.now().minusMinutes(6))));
        if (CollectionUtils.isEmpty(analysisRecords)) {
            log.info("没有需要分析的考核");
            return;
        }

        try(ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            log.info("开始补偿考核解析失败的考核");
            for (StudentAssessmentRecordPO record : analysisRecords) {
                Integer studentId = record.getStudentId();
                Integer assessmentId = record.getAssessmentId();
                // 获取考核设置信息
                Optional<AssessmentSettingPO> settingOp = settingService.findById(assessmentId);
                AssessmentSettingPO setting = settingOp.orElseThrow(() -> new BusinessException(ErrorCodeEnums.ASSESSMENT_SETTING_NOT_FOUND));

                List<StudentAssessmentQuestionAnswerPO> questionAnswers = questionAnswerService.listByAssessmentIdAndStudentId(assessmentId, studentId);
                executorService.execute(() -> {
                    try {
                        recordService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                                .set(StudentAssessmentRecordPO::getReAnalysis, true)
                                .set(StudentAssessmentRecordPO::getUpdateTime, LocalDateTime.now())
                                .eq(StudentAssessmentRecordPO::getId, record.getId()));
                        endAssessment(studentId, setting, questionAnswers, assessmentId);
                    } catch (Exception e) {
                        log.error("结束考核失败", e);
                        recordService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                                .set(StudentAssessmentRecordPO::getReAnalysis, false)
                                .eq(StudentAssessmentRecordPO::getId, record.getId()));
                    }
                });
            }
        }
    }

    /**
     * FIX-E: 手动重新分析单条考核记录（救评分卡死 / 重新生成评分）。
     * 仅允许对已完成(state=1)或分析中/失败(state=2)、且当前未在分析的记录触发；异步执行，立即返回。
     */
    public Boolean reanalyze(Integer recordId) {
        StudentAssessmentRecordPO record = recordService.getById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnums.RECORD_NOT_FOUND);
        }
        Integer state = record.getState();
        if (state == null || (state != 1 && state != 2)) {
            throw new BusinessException(132, "该考核记录当前状态不支持重新分析");
        }
        if (Objects.equals(record.getReAnalysis(), true)) {
            throw new BusinessException(132, "该记录正在分析中，请稍后再试");
        }
        Integer studentId = record.getStudentId();
        Integer assessmentId = record.getAssessmentId();
        AssessmentSettingPO setting = settingService.findById(assessmentId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnums.ASSESSMENT_SETTING_NOT_FOUND));
        List<StudentAssessmentQuestionAnswerPO> questionAnswers =
                questionAnswerService.listByAssessmentIdAndStudentId(assessmentId, studentId);
        threadPoolExecutor.execute(() -> {
            try {
                recordService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                        .set(StudentAssessmentRecordPO::getReAnalysis, true)
                        .set(StudentAssessmentRecordPO::getUpdateTime, LocalDateTime.now())
                        .eq(StudentAssessmentRecordPO::getId, recordId));
                endAssessment(studentId, setting, questionAnswers, assessmentId);
            } catch (Exception e) {
                log.error("手动重新分析失败, recordId:{}", recordId, e);
                recordService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                        .set(StudentAssessmentRecordPO::getReAnalysis, false)
                        .eq(StudentAssessmentRecordPO::getId, recordId));
            }
        });
        return true;
    }

    @Transactional(readOnly = true)
    public StudentDefenseStatisticDTO studentDefenseStatistic(int studentId) {
        // 学生所有的
        StudentSummary studentSummary = userService.findSummaryById(studentId).orElseThrow(() -> new BusinessException(ErrorCodeEnums.USER_NOT_FOUND));
        List<AssessmentStudentRelationPO> relations = studentSummary.getRelations();
        if (CollectionUtils.isEmpty(relations)) {
            return StudentDefenseStatisticDTO.empty();
        }

        return StudentDefenseStatisticDTO.builder()
                .all(relations.size())
                .toAppoint(studentSummary.toAppointAssessmentIds(this::noNeedAppointAssessmentIds, this::timeExpireAppointmentIds).size())
                .todo(studentSummary.todoAssessmentIds(this::noNeedAppointAssessmentIds).size())
                .analysis((int) studentSummary.getRecords().stream().filter(record -> Objects.equals(record.getState(), 2)).count())
                .done(studentSummary.doneAssessmentIds(this::timeExpireAppointmentIds).size())
                .build();
    }

    @Transactional(readOnly = true)
    public IPage<StudentTodoDefenseDTO> todoDefenseList(StudentDefensePagingQuery pagingQuery, Integer studentId) {
        int page = pagingQuery.getPage() != null ? pagingQuery.getPage() : 1;
        int size = pagingQuery.getSize() != null ? pagingQuery.getSize() : 10;
        // 全部的
        StudentSummary studentSummary = userService.findSummaryById(studentId).orElseThrow(() -> new BusinessException(ErrorCodeEnums.USER_NOT_FOUND));
        List<AssessmentStudentRelationPO> relations = studentSummary.getRelations();
        if (CollectionUtils.isEmpty(relations)) {
            return new Page<>(page, size);
        }
        List<Integer> todoAssessmentIds = studentSummary.todoAssessmentIds(this::noNeedAppointAssessmentIds);
        if (CollectionUtils.isEmpty(todoAssessmentIds)) {
            return new Page<>(page, size);
        }

        IPage<AssessmentSettingPO> poPage = settingService.page(new Page<>(page, size)
                , Wrappers.lambdaQuery(AssessmentSettingPO.class)
                        .like(StrUtil.isNotBlank(pagingQuery.getTheme()), AssessmentSettingPO::getTheme, pagingQuery.getTheme())
                        .in(AssessmentSettingPO::getId, todoAssessmentIds)
                        .orderByDesc(AssessmentSettingPO::getId));
        IPage<StudentTodoDefenseDTO> resultPage = poPage.convert(item -> StudentTodoDefenseDTO.builder()
                .id(item.getId())
                .theme(item.getTheme())
                .score(item.getTotalScore())
                .duration(item.getDuration())
                .questionCount(item.getQuestionCount())
                .answerTime(item.getAnswerTime())
                .question(item.getQuestion())
                .defense(item.getDefense())
                .digitalHumanId(item.getDigitalHumanId())
                .build());
        List<StudentTodoDefenseDTO> resultRecords = resultPage.getRecords();
        if (CollectionUtils.isNotEmpty(resultRecords)) {
            // 查询考核记录
            List<Integer> assessmentIds = resultRecords.stream().map(StudentTodoDefenseDTO::getId).distinct().toList();
            List<StudentAssessmentRecordPO> recordList = recordService.findByStudentIdAndAssessmentIds(studentId, assessmentIds);
            Map<Integer, StudentAssessmentRecordPO> assessmentIdMap = recordList.stream().collect(Collectors.toMap(StudentAssessmentRecordPO::getAssessmentId, record -> record));

            // 转换学生预约记录
            Map<Integer, StudentAssessmentAppointmentPO> assessmentAppointmentTimeMap = studentSummary.getAppointments().stream()
                    .collect(Collectors.toMap(StudentAssessmentAppointmentPO::getAssessmentId, item -> item, (v1, v2) -> v1));

            // 查询数字人信息
            List<DigitalHumanPO> allDigitalHumans = digitalHumanService.list();
            DigitalHumanDTO defaultDigitalHuman = allDigitalHumans.stream()
                    .filter(item -> Objects.equals(item.getDefaultFlag(), true))
                    .findFirst()
                    .map(this::toDigitalHumanDTO)
                    .orElse(null);

            // 查询考核的预约时间段信息
            Map<Integer, DigitalHumanDTO> digitalHumanIdMap = allDigitalHumans.stream()
                    .map(item -> DigitalHumanDTO.builder()
                            .id(item.getId())
                            .name(item.getName())
                            .video(getFileDTO(item.getVideo()))
                            .lipShape(getFileDTO(item.getLipShape()))
                            .defaultFlag(item.getDefaultFlag())
                            .build())
                    .collect(Collectors.toMap(DigitalHumanDTO::getId, item -> item));

            for (StudentTodoDefenseDTO resultRecord : resultRecords) {
                Optional.ofNullable(assessmentIdMap.get(resultRecord.getId()))
                        .ifPresent((value) -> resultRecord.setFinishDefense(isDefenseFinishedForCurrentTodo(value)));
                Optional.ofNullable(assessmentAppointmentTimeMap.get(resultRecord.getId()))
                        .ifPresent(iem -> {
                            resultRecord.setTimePeriod(iem.getTimePeriod());
                            resultRecord.setLocation(iem.getLocation());
                        });
                DigitalHumanDTO digitalHumanDTO = Optional.ofNullable(digitalHumanIdMap.get(resultRecord.getDigitalHumanId()))
                        .orElse(defaultDigitalHuman);
                resultRecord.setDigitalHuman(digitalHumanDTO);
            }
        }
        return resultPage;
    }

    private FileDTO getFileDTO(String file) {
        if (StringUtils.isBlank(file)) {
            return null;
        }
        FileDTO fileDTO = JSONUtil.toBean(file, FileDTO.class);
        fileDTO.setPresignedUrl(ossManger.getSignedUrl(fileDTO.getFileUrl()));
        return fileDTO;
    }

    private DigitalHumanDTO toDigitalHumanDTO(DigitalHumanPO digitalHumanPO) {
        if (digitalHumanPO == null) {
            return null;
        }
        return DigitalHumanDTO.builder()
                .id(digitalHumanPO.getId())
                .name(digitalHumanPO.getName())
                .video(getFileDTO(digitalHumanPO.getVideo()))
                .lipShape(getFileDTO(digitalHumanPO.getLipShape()))
                .defaultFlag(digitalHumanPO.getDefaultFlag())
                .build();
    }

    private List<Integer> noNeedAppointAssessmentIds(List<Integer> assessmentIds) {
        if (CollectionUtils.isEmpty(assessmentIds)) {
            return Collections.emptyList();
        }
        return settingService.list(Wrappers.lambdaQuery(AssessmentSettingPO.class)
                        .in(AssessmentSettingPO::getId, assessmentIds)
                        .eq(AssessmentSettingPO::getNeedAppoint, false))
                .stream()
                .map(AssessmentSettingPO::getId)
                .toList();
    }

    private List<Integer> timeExpireAppointmentIds(List<Integer> assessmentIds) {
        if (CollectionUtils.isEmpty(assessmentIds)) {
            return Collections.emptyList();
        }
        return settingService.list(Wrappers.lambdaQuery(AssessmentSettingPO.class)
                        .in(AssessmentSettingPO::getId, assessmentIds)
                        .lt(AssessmentSettingPO::getLastAppointTime, LocalDateTime.now()))
                .stream()
                .map(AssessmentSettingPO::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public IPage<StudentTodoDefenseDTO> toAppointmentList(StudentDefensePagingQuery pagingQuery, Integer studentId) {
        int page = pagingQuery.getPage() != null ? pagingQuery.getPage() : 1;
        int size = pagingQuery.getSize() != null ? pagingQuery.getSize() : 10;
        StudentSummary studentSummary = userService.findSummaryById(studentId).orElseThrow(() -> new BusinessException(ErrorCodeEnums.USER_NOT_FOUND));
        List<AssessmentStudentRelationPO> relations = studentSummary.getRelations();
        if (CollectionUtils.isEmpty(relations)) {
            return new Page<>(page, size);
        }
        List<Integer> toAppointIds = studentSummary.toAppointAssessmentIds(this::noNeedAppointAssessmentIds, this::timeExpireAppointmentIds);
        if (CollectionUtils.isEmpty(toAppointIds)) {
            return new Page<>(page, size);
        }
        IPage<AssessmentSettingPO> poPage = settingService.page(new Page<>(page, size)
                , Wrappers.lambdaQuery(AssessmentSettingPO.class)
                        .in(AssessmentSettingPO::getId, toAppointIds)
                        .eq(AssessmentSettingPO::getNeedAppoint, true)
                        .like(StrUtil.isNotBlank(pagingQuery.getTheme()), AssessmentSettingPO::getTheme, pagingQuery.getTheme())
                        .orderByDesc(AssessmentSettingPO::getId));
        List<AssessmentSettingPO> records = poPage.getRecords();
        Map<Integer, List<AssessmentAppointmentSettingPO>> asessmentAppointSettingMap = new HashMap<>();
        Map<Integer, LinkedHashMap<LocalDateTime, Long>> assessmentTimeCountMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(records)) {
            List<Integer> assessmentIds = records.stream().map(AssessmentSettingPO::getId).toList();
            asessmentAppointSettingMap.putAll(asessmentAppointSettingMap(assessmentIds));
            assessmentTimeCountMap.putAll(assessmentTimeCountMap(assessmentIds));
        }
        return poPage.convert(item -> StudentTodoDefenseDTO.builder()
                .id(item.getId())
                .theme(item.getTheme())
                .score(item.getTotalScore())
                .duration(item.getDuration())
                .questionCount(item.getQuestionCount())
                .answerTime(item.getAnswerTime())
                .question(item.getQuestion())
                .defense(item.getDefense())
                .canAppointmentTimes(asessmentAppointSettingMap.getOrDefault(item.getId(), Collections.emptyList())
                        .stream()
                        .map(entry -> {
                            LinkedHashMap<LocalDateTime, Long> timePeriodCountMap = assessmentTimeCountMap.getOrDefault(item.getId(), new LinkedHashMap<>());
                            Integer appointCount = timePeriodCountMap.getOrDefault(entry.getTimePeriod(), 0L).intValue();
                            return StudentTodoDefenseDTO.CanAppointmentTimeDTO.builder()
                                    .timePeriod(entry.getTimePeriod())
                                    .location(entry.getLocation())
                                    .full(appointCount >= entry.getParticipantLimit())
                                    .remainCount(entry.getParticipantLimit() - appointCount)
                                    .build();
                        })
                        .toList())
                .build());
    }

    private Map<Integer, List<AssessmentAppointmentSettingPO>> asessmentAppointSettingMap(List<Integer> assessmentIds) {
        return appointmentSettingService.list(Wrappers.lambdaQuery(AssessmentAppointmentSettingPO.class)
                        .gt(AssessmentAppointmentSettingPO::getTimePeriod, LocalDateTime.now())
                        .in(AssessmentAppointmentSettingPO::getAssessmentId, assessmentIds))
                .stream()
                .filter(setting -> setting.getTimePeriod() != null)
                .collect(Collectors.groupingBy(
                        AssessmentAppointmentSettingPO::getAssessmentId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                groupedList -> groupedList.stream()
                                        .filter(setting -> setting.getTimePeriod() != null)
                                        .sorted(Comparator.comparing(AssessmentAppointmentSettingPO::getTimePeriod))
                                        .toList()
                        )
                ));
    }

    private Map<Integer, LinkedHashMap<LocalDateTime, Long>> assessmentTimeCountMap(List<Integer> assessmentIds) {
        return studentAppointmentService.list(Wrappers.lambdaQuery(StudentAssessmentAppointmentPO.class)
                        .in(StudentAssessmentAppointmentPO::getAssessmentId, assessmentIds)
                        .and(query -> query.in(StudentAssessmentAppointmentPO::getState, APPOINTMENT_CONFIRMED, APPOINTMENT_IN_ASSESSMENT)
                                .or()
                                .isNull(StudentAssessmentAppointmentPO::getState)))
                .stream()
                .collect(Collectors.groupingBy(
                        StudentAssessmentAppointmentPO::getAssessmentId,
                        Collectors.collectingAndThen(
                                Collectors.groupingBy(
                                        StudentAssessmentAppointmentPO::getTimePeriod,
                                        Collectors.counting()
                                ),
                                innerMap -> {
                                    // 按 timePeriod 从小到大排序
                                    return innerMap.entrySet().stream()
                                            .sorted(Map.Entry.comparingByKey())
                                            .collect(Collectors.toMap(
                                                    Map.Entry::getKey,
                                                    Map.Entry::getValue,
                                                    (oldValue, newValue) -> oldValue,
                                                    LinkedHashMap::new // 保证有序
                                            ));
                                }
                        )
                ));
    }

    @Transactional(readOnly = true)
    public IPage<StudentTodoDefenseDTO> analysisDefenseList(StudentDefensePagingQuery pagingQuery, Integer studentId) {
        int page = pagingQuery.getPage() != null ? pagingQuery.getPage() : 1;
        int size = pagingQuery.getSize() != null ? pagingQuery.getSize() : 10;
        List<Integer> analyzeIds = recordService.list(Wrappers.lambdaQuery(StudentAssessmentRecordPO.class)
                        .eq(StudentAssessmentRecordPO::getStudentId, studentId)
                        .eq(StudentAssessmentRecordPO::getState, 2))
                .stream()
                .map(StudentAssessmentRecordPO::getAssessmentId)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(analyzeIds)) {
            return new Page<>(page, size);
        }

        IPage<AssessmentSettingPO> poPage = settingService.page(new Page<>(page, size)
                , Wrappers.lambdaQuery(AssessmentSettingPO.class)
                        .in(AssessmentSettingPO::getId, analyzeIds)
                        .like(StrUtil.isNotBlank(pagingQuery.getTheme()), AssessmentSettingPO::getTheme, pagingQuery.getTheme())
                        .orderByDesc(AssessmentSettingPO::getId));
        IPage<StudentTodoDefenseDTO> resultPage = poPage.convert(item -> StudentTodoDefenseDTO.builder()
                .id(item.getId())
                .theme(item.getTheme())
                .score(item.getTotalScore())
                .duration(item.getDuration())
                .questionCount(item.getQuestionCount())
                .answerTime(item.getAnswerTime())
                .question(item.getQuestion())
                .defense(item.getDefense())
                .build());
        List<StudentTodoDefenseDTO> resultRecords = resultPage.getRecords();
        if (CollectionUtils.isNotEmpty(resultRecords)) {
            List<Integer> assessmentIds = resultRecords.stream().map(StudentTodoDefenseDTO::getId).distinct().toList();
            List<StudentAssessmentRecordPO> recordList = recordService.findByStudentIdAndAssessmentIds(studentId, assessmentIds);
            Map<Integer, StudentAssessmentRecordPO> assessmentIdMap = recordList.stream().collect(Collectors.toMap(StudentAssessmentRecordPO::getAssessmentId, record -> record));
            for (StudentTodoDefenseDTO resultRecord : resultRecords) {
                Optional.ofNullable(assessmentIdMap.get(resultRecord.getId()))
                        .ifPresent((value) -> {
                            resultRecord.setFinishDefense(value.getEndDefenseTime() != null);
                        });
            }
        }
        return resultPage;
    }

    @Transactional(readOnly = true)
    public IPage<StudentDoneDefenseDTO> doneDefenseList(StudentDefensePagingQuery pagingQuery, Integer studentId) {
        int page = pagingQuery.getPage() != null ? pagingQuery.getPage() : 1;
        int size = pagingQuery.getSize() != null ? pagingQuery.getSize() : 10;
        // 全部的
        StudentSummary studentSummary = userService.findSummaryById(studentId).orElseThrow(() -> new BusinessException(ErrorCodeEnums.USER_NOT_FOUND));
        List<AssessmentStudentRelationPO> relations = studentSummary.getRelations();
        if (CollectionUtils.isEmpty(relations)) {
            return new Page<>(page, size);
        }
        List<Integer> completeAppointAssessmentIds = studentSummary.doneAssessmentIds(this::timeExpireAppointmentIds);
        if (CollectionUtils.isEmpty(completeAppointAssessmentIds)) {
            return new Page<>(page, size);
        }
        IPage<AssessmentSettingPO> poPage = settingService.page(new Page<>(page, size)
                , Wrappers.lambdaQuery(AssessmentSettingPO.class)
                        .in(AssessmentSettingPO::getId, completeAppointAssessmentIds)
                        .like(StrUtil.isNotBlank(pagingQuery.getTheme()), AssessmentSettingPO::getTheme, pagingQuery.getTheme())
                        .orderByDesc(AssessmentSettingPO::getId));
        List<AssessmentSettingPO> records = poPage.getRecords();
        if (CollectionUtils.isNotEmpty(records)) {
            List<Integer> assessmentIds = records.stream().map(AssessmentSettingPO::getId).toList();
            Map<Integer, List<AssessmentAppointmentSettingPO>> appointMap = asessmentAppointSettingMap(assessmentIds);
            Map<Integer, LinkedHashMap<LocalDateTime, Long>> studentTimeCountMap = assessmentTimeCountMap(assessmentIds);
            Map<Integer, AssessmentSettingPO> assessmentIdMap = settingService.listByIds(assessmentIds).stream()
                    .collect(Collectors.toMap(AssessmentSettingPO::getId, item -> item));

            Map<Integer, StudentAssessmentRecordPO> studentRecordMap = recordService.list(Wrappers.lambdaQuery(StudentAssessmentRecordPO.class)
                            .eq(StudentAssessmentRecordPO::getStudentId, studentId)
                            .in(StudentAssessmentRecordPO::getAssessmentId, assessmentIds))
                    .stream()
                    .collect(Collectors.toMap(StudentAssessmentRecordPO::getAssessmentId, item -> item));
            Map<Integer, StudentAssessmentAppointmentPO> studentAppointMap = studentSummary.getAppointments().stream()
                    .collect(Collectors.toMap(StudentAssessmentAppointmentPO::getAssessmentId, item -> item));
            return poPage.convert(item -> {
                AssessmentSettingPO assessmentSettingPO = assessmentIdMap.get(item.getId());
                StudentAssessmentRecordPO record = studentRecordMap.getOrDefault(item.getId(), null);
                StudentAssessmentAppointmentPO studentAssessmentAppointmentPO = studentAppointMap.get(item.getId());
                // 计算状态
                Pair<Integer, String> state = calculateState(assessmentSettingPO, record, studentAssessmentAppointmentPO);
                return StudentDoneDefenseDTO.builder()
                        .id(item.getId())
                        .theme(assessmentSettingPO.getTheme())
                        .score(assessmentSettingPO.getTotalScore())
                        .duration(assessmentSettingPO.getDuration())
                        .assessStartTime(Optional.ofNullable(record).map(StudentAssessmentRecordPO::getCreateTime).orElse(null))
                        .assessmentEndTime(Optional.ofNullable(record).map(StudentAssessmentRecordPO::getUpdateTime).orElse(null))
                        .defenseScore(Optional.ofNullable(record).map(StudentAssessmentRecordPO::frontScore).orElse(null))
                        .state(state.getFirst())
                        .stateTag(state.getSecond())
                        .passScore(item.getPassScore())
                        .assessmentFailPunish(assessmentSettingPO.getAssessmentFailPunish())
                        .rescheduleAppointTime(assessmentSettingPO.getRescheduleAppointTime())
                        .showResult(assessmentSettingPO.getShowResult())
                        .timePeriod(studentAssessmentAppointmentPO == null ? null : studentAssessmentAppointmentPO.getTimePeriod())
                        .location(studentAssessmentAppointmentPO == null ? null : studentAssessmentAppointmentPO.getLocation())
                        .appointRecordFlag(studentAppointMap.get(item.getId()) != null)
                        .canAppointmentTimes(appointMap.getOrDefault(item.getId(), Collections.emptyList())
                                .stream()
                                .map(entry -> {
                                    Integer appointCount = studentTimeCountMap.getOrDefault(item.getId(), new LinkedHashMap<>()).getOrDefault(entry.getTimePeriod(), 0L).intValue();
                                    return StudentDoneDefenseDTO.CanAppointmentTimeDTO.builder()
                                            .timePeriod(entry.getTimePeriod())
                                            .location(entry.getLocation())
                                            .full(appointCount >= entry.getParticipantLimit())
                                            .remainCount(entry.getParticipantLimit() - appointCount)
                                            .build();
                                })
                                .toList())
                        .build();
            });
        }
        return new Page<>(poPage.getCurrent(), poPage.getSize(), poPage.getTotal());
    }

    private Pair<Integer, String> calculateState(AssessmentSettingPO setting,
                                                 StudentAssessmentRecordPO record,
                                                 StudentAssessmentAppointmentPO appointment) {
        Boolean assessmentFailPunish = setting.getAssessmentFailPunish();
        LocalDateTime rescheduleAppointTime = setting.getRescheduleAppointTime();

        int state;
        String stateTag = "";
        // 能在已完成的记录，都是已经通过的
        if (record != null && Objects.equals(record.getState(), 1)) {
            state = 2;
        }
        // 如果有预约的，则返回预约的状态
        else if (appointment != null) {
            state = appointment.getState();
            stateTag = "预约未考";
            // 对于预约未考，需要判断是否过了可重新预约时间
            if (Objects.equals(appointment.getState(), 3)) {
                if (Objects.equals(assessmentFailPunish, true) && Objects.nonNull(rescheduleAppointTime) && rescheduleAppointTime.isBefore(LocalDateTime.now())) {
                    state = 4;
                }
                // 如果没有惩罚，则可以直接重新预约
                if (Objects.equals(assessmentFailPunish, false)) {
                    state = 4;
                }
            }
        }
        // 如果没有考核，也没有预约，那就是过期未预约的
        else {
            state = 5;
            stateTag = "过期未预约";
            if (setting.getLastAppointTime() != null && setting.getLastAppointTime().isBefore(LocalDateTime.now())) {
                // 如果有惩罚，并且过了可重新预约时间，则返回可重新预约
                if (Objects.equals(assessmentFailPunish, true) && Objects.nonNull(rescheduleAppointTime) && rescheduleAppointTime.isBefore(LocalDateTime.now())) {
                    state = 4;
                }
            }
        }
        return new Pair<>(state, stateTag);
    }

    /**
     * 处理 WebSocket 消息，根据消息内容执行不同的业务逻辑。
     * 支持的业务操作包括：开始答辩、结束答辩、开始作答、结束作答、开始考核、结束考核等。
     *
     * @param studentId  学生ID，标识当前操作的学生
     * @param messageDTO WebSocket消息数据传输对象，包含具体的操作类型和消息内容
     * @param consumer   WebSocket响应结果的回调函数，用于向客户端发送处理结果
     */
    public void handleWebsocketMessage(Integer studentId, WebsocketMessageDTO messageDTO, Consumer<WebSocketResponse> consumer) {
        String message = messageDTO.getMessage();
        int assessmentId = Integer.parseInt(message);

        // 1.判断并处理开始答辩请求
        if (messageDTO.startDefense()) {
            startDefense(studentId, consumer, assessmentId);
            return;
        }

        // 2.判断并处理结束答辩请求
        if (messageDTO.endDefense()) {
            endDefense(studentId, consumer, assessmentId);
            return;
        }

        // 3.开始考核
        if (messageDTO.startAssessment()) {
            startAssessment(studentId, consumer, assessmentId);
            return;
        }

        // 5.判断并处理开始作答请求
        if (messageDTO.startAnswer()) {
            startAnswer(studentId, consumer, assessmentId);
            return;
        }

        // 6.判断并处理结束作答请求
        if (messageDTO.endAnswer()) {
            endAnswer(studentId, consumer, assessmentId);
            return;
        }

        // 获取考核设置信息
        Optional<AssessmentSettingPO> settingOp = settingService.findById(assessmentId);
        AssessmentSettingPO setting = settingOp.orElseThrow(() -> new BusinessException(ErrorCodeEnums.ASSESSMENT_SETTING_NOT_FOUND));

        // 获取学生考核记录
        Optional<StudentAssessmentRecordPO> studentAssessment = recordService.findByStudentIdAndAssessmentId(studentId, assessmentId);
        StudentAssessmentRecordPO record = studentAssessment.orElseGet(() -> {
            StudentAssessmentRecordPO record1 = StudentAssessmentRecordPO.builder()
                    .studentId(studentId)
                    .assessmentId(assessmentId)
                    .build();
            recordService.save(record1);
            return record1;
        });

        // 获取学生考核题目作答记录
        List<StudentAssessmentQuestionAnswerPO> questionAnswerPOS = questionAnswerService.listByAssessmentIdAndStudentId(assessmentId, studentId);

        // 4.生成问题
        if (messageDTO.generateQuestion()) {
            generateQuestionOrFollowQuestion(studentId, consumer, setting, questionAnswerPOS, assessmentId, record);
            return;
        }

        // 7.判断并处理结束考核请求
        if (messageDTO.endAssessment()) {
            // 清理内存缓存
            clearInterviewQuestionState(studentId, assessmentId, setting.getQuestionCount());
            VOICE_MAP.remove(studentId);

            // 先更新为分析中
            recordService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                    .set(StudentAssessmentRecordPO::getState, 2)
                    .set(StudentAssessmentRecordPO::getReAnalysis, true)
                    .eq(StudentAssessmentRecordPO::getStudentId, studentId)
                    .eq(StudentAssessmentRecordPO::getAssessmentId, assessmentId));

            // 更新预约已完成
            studentAppointmentService.update(Wrappers.lambdaUpdate(StudentAssessmentAppointmentPO.class)
                    .set(StudentAssessmentAppointmentPO::getState, 2)
                    .eq(StudentAssessmentAppointmentPO::getStudentId, studentId)
                    .eq(StudentAssessmentAppointmentPO::getAssessmentId, assessmentId));

            threadPoolExecutor.execute(() -> {
                try {
                    endAssessment(studentId, setting, questionAnswerPOS, assessmentId);
                } catch (Exception e) {
                    log.error("异步分析考核失败, studentId:{}, assessmentId:{}", studentId, assessmentId, e);
                    recordService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                            .set(StudentAssessmentRecordPO::getReAnalysis, false)
                            .eq(StudentAssessmentRecordPO::getStudentId, studentId)
                            .eq(StudentAssessmentRecordPO::getAssessmentId, assessmentId));
                    WebsocketManager.sendTextMessage(studentId, WebSocketResponse.of("考核分析失败，系统将自动重试"));
                }
            });
        }
    }

    private void startAssessment(Integer studentId, Consumer<WebSocketResponse> consumer, int assessmentId) {
        AssessmentSettingPO setting = settingService.getById(assessmentId);
        // 清除之前的问答记录
        questionAnswerService.clearQuestionAnswer(studentId, assessmentId);
        clearInterviewQuestionState(studentId, assessmentId, setting.getQuestionCount());

        // 考核中
        studentAppointmentService.update(Wrappers.lambdaUpdate(StudentAssessmentAppointmentPO.class)
                .set(StudentAssessmentAppointmentPO::getState, 1)
                .eq(StudentAssessmentAppointmentPO::getAssessmentId, assessmentId)
                .eq(StudentAssessmentAppointmentPO::getStudentId, studentId));
        consumer.accept(WebSocketResponse.of("开始考核"));

        // 异步现场生成题目
        consumer.accept(WebSocketResponse.of("正在生成题目，请稍候..."));
        threadPoolExecutor.execute(() -> {
            try {
                // 获取答辩转写内容
                String defenseContent = "";
                Optional<StudentAssessmentRecordPO> recordOp = recordService.findByStudentIdAndAssessmentId(studentId, assessmentId);
                if (recordOp.isPresent() && StringUtils.isNotBlank(recordOp.get().getDefenseContent())) {
                    defenseContent = recordOp.get().getDefenseContent();
                }

                // 获取老师上传 PDF 的预解析材料，避免每个学生重复下载、上传和解析 PDF。
                String materialContext = assessmentMaterialService.getOrParseMaterialContext(setting);

                // 调用 AI 生成现场题目
                String result = geminiManager.generateLiveQuestions(
                        defenseContent,
                        materialContext,
                        setting.getAssessmentCriteria(),
                        setting.getAssessmentRequirements(),
                        setting.getQuestionCount()
                );

                // 解析 JSON 结果（AI 可能返回 markdown 包裹的 JSON）
                String jsonStr = result.trim();
                if (jsonStr.startsWith("```")) {
                    int startIdx = jsonStr.indexOf('\n') + 1;
                    int endIdx = jsonStr.lastIndexOf("```");
                    if (endIdx > startIdx) {
                        jsonStr = jsonStr.substring(startIdx, endIdx).trim();
                    }
                }
                cn.hutool.json.JSONObject jsonResult = JSONUtil.parseObj(jsonStr);
                cn.hutool.json.JSONArray questions = jsonResult.getJSONArray("questions");
                List<LiveQuestion> liveQuestions = normalizeLiveQuestionsForInterview(questions, setting.getQuestionCount());
                String mapKey = studentId + ":" + assessmentId;
                cacheLiveQuestions(mapKey, studentId, assessmentId, liveQuestions);
                log.info("现场题目生成完成, studentId:{}, assessmentId:{}, count:{}", studentId, assessmentId, liveQuestions.size());
                // FIX-F: 题目生成完成后直接推送第 1 题（纯文本推送，避免与 TTS 播放器竞态），
                // 不再让学生手动点击「获取题目」。前端 QuestionPage 已停止在 start-assessment 后自动请求。
                if (CollectionUtils.isNotEmpty(liveQuestions)) {
                    GeneratedQuestionDTO firstQuestion = new GeneratedQuestionDTO();
                    firstQuestion.setQuestion(liveQuestions.get(0).getQuestion());
                    firstQuestion.setFollow(false);
                    sendQuestionTextToStudent(firstQuestion, studentId, assessmentId, 1, setting.getQuestionCount(),
                            response -> WebsocketManager.sendTextMessage(studentId, response));
                }
            } catch (Exception e) {
                log.error("现场题目生成失败, studentId:{}, assessmentId:{}", studentId, assessmentId, e);
                WebsocketManager.sendTextMessage(studentId, WebSocketResponse.of("题目生成失败，请重试"));
            }
        });
    }

    private void generateQuestionOrFollowQuestion(Integer studentId,
                                                  Consumer<WebSocketResponse> consumer,
                                                  AssessmentSettingPO setting,
                                                  List<StudentAssessmentQuestionAnswerPO> questionAnswerPOS,
                                                  Integer assessmentId,
                                                  StudentAssessmentRecordPO record) {

        String mapKey = studentId + ":" + assessmentId;
        Integer questionCount = setting.getQuestionCount();
        int answeredCount = questionAnswerPOS.size();

        // 检查是否还有追问需要发送
        if (CollectionUtils.isNotEmpty(questionAnswerPOS)) {
            StudentAssessmentQuestionAnswerPO lastAnswer = questionAnswerPOS.getLast();
            if (answeredCount <= questionCount) {
                String followUpKey = mapKey + ":" + answeredCount;
                String followUp = getCachedFollowUp(followUpKey, studentId, assessmentId, answeredCount);
                if (StringUtils.isNotBlank(followUp) && StringUtils.isBlank(lastAnswer.getFollowQuestion())) {
                    // 发送追问
                    GeneratedQuestionDTO followDTO = new GeneratedQuestionDTO();
                    followDTO.setQuestion(followUp);
                    followDTO.setFollow(true);
                    sendQuestionToStudent(followDTO, studentId, assessmentId, answeredCount, questionCount, consumer);
                    return;
                }
            }
            // 已回答足够题目，结束
            if (answeredCount >= questionCount) {
                sendEndMessage(answeredCount, questionCount, consumer);
                return;
            }
        }

        // 从现场生成的题目列表中取下一题
        List<LiveQuestion> liveQuestions = getCachedLiveQuestions(mapKey, studentId, assessmentId, questionCount);
        if (CollectionUtils.isEmpty(liveQuestions)) {
            // 题目尚未生成完成，提示等待
            consumer.accept(WebSocketResponse.of("题目正在生成中，请稍候再试..."));
            return;
        }
        if (answeredCount >= liveQuestions.size()) {
            sendEndMessage(answeredCount, questionCount, consumer);
            return;
        }

        LiveQuestion nextQuestion = liveQuestions.get(answeredCount);
        GeneratedQuestionDTO questionDTO = new GeneratedQuestionDTO();
        questionDTO.setQuestion(nextQuestion.getQuestion());
        questionDTO.setFollow(false);
        sendQuestionToStudent(questionDTO, studentId, assessmentId, answeredCount + 1, questionCount, consumer);
    }

    private void sendQuestionToStudent(GeneratedQuestionDTO question, Integer studentId, Integer assessmentId,
                                        int index, int total, Consumer<WebSocketResponse> consumer) {
        sendQuestionTextToStudent(question, studentId, assessmentId, index, total, consumer);

        WebsocketManager.startVoiceSending(studentId);
        try {
            boolean hasVoice = ttsManager.streamVoice(question.getQuestion(), chunk -> {
                if (!WebsocketManager.isVoiceSendingInterrupted(studentId)) {
                    WebsocketManager.sendVoiceChunk(chunk, studentId);
                }
            });
            if (!hasVoice) {
                log.warn("TTS未返回音频, studentId:{}, question:{}", studentId, question.getQuestion());
            }
        } catch (Exception e) {
            log.error("TTS生成失败, studentId:{}, question:{}", studentId, question.getQuestion(), e);
        } finally {
            WebsocketManager.sendVoiceEnd(studentId);
            WebsocketManager.finishVoiceSending(studentId);
        }
    }

    private void sendQuestionTextToStudent(GeneratedQuestionDTO question, Integer studentId, Integer assessmentId,
                                           int index, int total, Consumer<WebSocketResponse> consumer) {
        QuestionDTO questionDTO = QuestionDTO.builder()
                .index(index)
                .total(total)
                .title(question.getQuestion())
                .follow(question.getFollow())
                .last(index >= total)
                .build();
        redisTemplate.opsForValue().set(RedisConstants.STUDENT_QUESTION.formatted(studentId, assessmentId),
                JSONUtil.toJsonStr(question), 10, TimeUnit.MINUTES);
        consumer.accept(WebSocketResponse.of(JSONUtil.toJsonStr(questionDTO)));
    }

    private void sendEndMessage(int answeredCount, int questionCount, Consumer<WebSocketResponse> consumer) {
        QuestionDTO questionDTO = QuestionDTO.builder()
                .index(answeredCount)
                .total(questionCount)
                .title("结束问答")
                .follow(false)
                .last(true)
                .build();
        consumer.accept(WebSocketResponse.of(JSONUtil.toJsonStr(questionDTO)));
    }

    private String getVoiceContent(String content, String voiceUrl) {
        if (StringUtils.isBlank(content)) {
            FileByteDTO answerVoiceBytes = httpUtils.downloadFile(voiceUrl);
            if (answerVoiceBytes == null) {
                return "没有回答";
            }
            return getVoiceContent(answerVoiceBytes.getBytes());
        }
        return content;
    }

    private String getVoiceContent(byte[] bytes) {
        if (bytes == null) {
            return "没有回答";
        }
        try {
            return geminiManager.pickVoiceContent(bytes);
        } catch (Exception e) {
            return "没有回答";
        }
    }

    private String getRealtimeVoiceContentOrFallback(Integer studentId, byte[] bytes) {
        return realtimeSpeechManager.completeTurnAndAwaitTranscript(studentId)
                .filter(StringUtils::isNotBlank)
                .orElseGet(() -> getVoiceContent(bytes));
    }

    private void endAssessment(Integer studentId,
                               AssessmentSettingPO setting,
                               List<StudentAssessmentQuestionAnswerPO> questionAnswerPOS,
                               int assessmentId) {

        Boolean isQuestion = setting.getQuestion();
        Boolean isDefense = setting.getDefense();
        // 答辩 + 问题
        StringBuilder question = new StringBuilder();
        String schemaType;
        if (isDefense && isQuestion) {
            question.append("对学生的答辩和考核问答进行考核");
            String defenseContent = generateDefenseContent(studentId, assessmentId);
            question.append("【学生答辩】").append("\n")
                    .append(defenseContent).append("\n");
            question.append("【考核问答】").append("\n");
            for (StudentAssessmentQuestionAnswerPO questionAnswerPO : questionAnswerPOS) {
                question.append("问题Id：").append(questionAnswerPO.getId()).append("\n")
                        .append("问题：").append(questionAnswerPO.getQuestion()).append("\n")
                        .append("回答：").append(getVoiceContent(questionAnswerPO.getAnswer(), questionAnswerPO.getAnswerVoice())).append("\n");
                if (StringUtils.isNotBlank(questionAnswerPO.getFollowQuestion())) {
                    question.append("追问问题：").append(questionAnswerPO.getFollowQuestion()).append("\n")
                            .append("追加问题回答：").append(getVoiceContent(questionAnswerPO.getFollowAnswer(), questionAnswerPO.getFollowAnswerVoice())).append("\n");
                }
            }
            schemaType = "defense_question";
        } else if (isQuestion) {
            question.append("对学生考核问答进行考核");
            question.append("【学生答辩】").append("\n")
                    .append("无").append("\n");
            question.append("【考核问答】").append("\n");
            for (StudentAssessmentQuestionAnswerPO questionAnswerPO : questionAnswerPOS) {
                question.append("问题Id：").append(questionAnswerPO.getId()).append("\n")
                        .append("问题：").append(questionAnswerPO.getQuestion()).append("\n")
                        .append("回答：").append(getVoiceContent(questionAnswerPO.getAnswer(), questionAnswerPO.getAnswerVoice())).append("\n");
                if (StringUtils.isNotBlank(questionAnswerPO.getFollowQuestion())) {
                    question.append("追问问题：").append(questionAnswerPO.getFollowQuestion()).append("\n")
                            .append("追加问题回答：").append(getVoiceContent(questionAnswerPO.getFollowAnswer(), questionAnswerPO.getFollowAnswerVoice())).append("\n");
                }
            }
            schemaType = "question";
        } else {
            question.append("对学生的答辩进行考核");
            String defenseContent = generateDefenseContent(studentId, assessmentId);
            question.append("【学生答辩】").append("\n")
                    .append(defenseContent).append("\n")
                    .append("【考核问答】").append("\n")
                    .append("空");
            schemaType = "defense";
        }
        log.info("endAssessment prompt:{}", question);
        String materialContext = assessmentMaterialService.getOrParseMaterialContext(setting);
        AssessmentEvaluationDTO results = geminiManager.summaryAnalysis(buildSummarySystemInstruction(setting), question.toString(), materialContext, schemaType);
        recordService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                .set(StudentAssessmentRecordPO::getDefenseResult, JSONUtil.toJsonStr(results))
                .set(StudentAssessmentRecordPO::getState, 1)
                .set(StudentAssessmentRecordPO::getReAnalysis, false)
                .eq(StudentAssessmentRecordPO::getStudentId, studentId)
                .eq(StudentAssessmentRecordPO::getAssessmentId, assessmentId));
    }

    private String generateDefenseContent(Integer studentId, int assessmentId) {
        Optional<StudentAssessmentRecordPO> assessmentRecordOp = recordService.findByStudentIdAndAssessmentId(studentId, assessmentId);
        StudentAssessmentRecordPO record = assessmentRecordOp.orElseThrow(() -> new BusinessException(ErrorCodeEnums.STUDENT_ASSESSMENT_NOT_FOUND));
        String voiceUrl = record.getDefenseVoice();
        String defenseContent = record.getDefenseContent();
        if (StringUtils.isNotBlank(defenseContent)) {
            return defenseContent;
        }
        FileByteDTO voiceBytes = httpUtils.downloadFile(voiceUrl);
        return getVoiceContent(voiceBytes.getBytes());
    }

    private void endAnswer(Integer studentId, Consumer<WebSocketResponse> consumer, int assessmentId) {
        ByteArrayOutputStream byteArrayOutputStream = VOICE_MAP.get(studentId);
        if (byteArrayOutputStream == null) {
            consumer.accept(WebSocketResponse.ofLast("未找到录音数据"));
            return;
        }
        if (!hasValidAudio(byteArrayOutputStream)) {
            VOICE_MAP.remove(studentId);
            consumer.accept(WebSocketResponse.ofError("未检测到有效录音，请重新回答"));
            return;
        }
        String currentQuestionKey = RedisConstants.STUDENT_QUESTION.formatted(studentId, assessmentId);
        String generatedQuestionString = (String) redisTemplate.opsForValue().get(currentQuestionKey);
        if (generatedQuestionString == null) {
            VOICE_MAP.remove(studentId);
            consumer.accept(WebSocketResponse.ofLast("题目已过期，请重新获取"));
            return;
        }
        byte[] voiceBytes = addWavHeader(byteArrayOutputStream.toByteArray());
        String voiceUrl = ossManger.uploadVoice(voiceBytes);
        GeneratedQuestionDTO questionDTO = JSONUtil.toBean(generatedQuestionString, GeneratedQuestionDTO.class);
        if (questionDTO.getFollow()) {
            // 追问回答
            VOICE_MAP.remove(studentId);
            Optional<StudentAssessmentQuestionAnswerPO> lastQuestionAnswerOp = questionAnswerService.lastQuestionAnswer(studentId, assessmentId);
            if (lastQuestionAnswerOp.isEmpty()) {
                redisTemplate.delete(currentQuestionKey);
                consumer.accept(WebSocketResponse.ofLast("未找到上一题记录"));
                return;
            }
            StudentAssessmentQuestionAnswerPO questionAnswer = lastQuestionAnswerOp.get();
            questionAnswer.setFollowQuestion(questionDTO.getQuestion());
            questionAnswer.setFollowAnswerVoice(voiceUrl);
            questionAnswerService.updateById(questionAnswer);
            redisTemplate.delete(currentQuestionKey);
            consumer.accept(WebSocketResponse.ofLast("结束答题"));
            threadPoolExecutor.execute(() -> {
                try {
                    String voiceText = getRealtimeVoiceContentOrFallback(studentId, voiceBytes);
                    questionAnswerService.update(Wrappers.lambdaUpdate(StudentAssessmentQuestionAnswerPO.class)
                            .set(StudentAssessmentQuestionAnswerPO::getFollowAnswer, voiceText)
                            .eq(StudentAssessmentQuestionAnswerPO::getId, questionAnswer.getId()));
                } catch (Exception e) {
                    log.error("追问回答转写失败, studentId:{}, assessmentId:{}", studentId, assessmentId, e);
                    WebsocketManager.sendTextMessage(studentId, WebSocketResponse.of("追问回答处理失败，将进入下一题"));
                } finally {
                    WebsocketManager.sendTextMessage(studentId, WebSocketResponse.of("题目生成完成，请点击获取题目"));
                }
            });
        } else {
            // 普通题目回答
            StudentAssessmentQuestionAnswerPO questionAnswerPO = StudentAssessmentQuestionAnswerPO.builder()
                    .assessmentId(assessmentId)
                    .studentId(studentId)
                    .question(questionDTO.getQuestion())
                    .answerVoice(voiceUrl)
                    .build();
            questionAnswerService.save(questionAnswerPO);
            VOICE_MAP.remove(studentId);
            redisTemplate.delete(currentQuestionKey);
            consumer.accept(WebSocketResponse.ofLast("结束答题"));

            // 异步转写，并在追问生成完成后再通知前端获取下一条题目
            threadPoolExecutor.execute(() -> {
                AtomicBoolean directFollowUpSent = new AtomicBoolean(false);
                try {
                    AssessmentSettingPO setting = settingService.getById(assessmentId);
                    if (Objects.equals(setting.getFollowUp(), true)) {
                        List<StudentAssessmentQuestionAnswerPO> answers = questionAnswerService.listByAssessmentIdAndStudentId(assessmentId, studentId);
                        int questionIndex = answers.size();
                        String followUpKey = studentId + ":" + assessmentId + ":" + questionIndex;
                        WebsocketManager.sendTextMessage(studentId, WebSocketResponse.of("正在生成追问，请稍候..."));
                        AtomicReference<String> realtimeAnswerText = new AtomicReference<>();
                        if (tryGenerateAndSendRealtimeFollowUp(studentId, assessmentId, setting, questionDTO,
                                followUpKey, questionIndex, realtimeAnswerText)) {
                            String voiceText = realtimeAnswerOrFallback(realtimeAnswerText.get(), voiceBytes);
                            questionAnswerService.update(Wrappers.lambdaUpdate(StudentAssessmentQuestionAnswerPO.class)
                                    .set(StudentAssessmentQuestionAnswerPO::getAnswer, voiceText)
                                    .eq(StudentAssessmentQuestionAnswerPO::getId, questionAnswerPO.getId()));
                            directFollowUpSent.set(true);
                            return;
                        }
                        String voiceText = realtimeAnswerOrFallback(realtimeAnswerText.get(), voiceBytes);
                        questionAnswerService.update(Wrappers.lambdaUpdate(StudentAssessmentQuestionAnswerPO.class)
                                .set(StudentAssessmentQuestionAnswerPO::getAnswer, voiceText)
                                .eq(StudentAssessmentQuestionAnswerPO::getId, questionAnswerPO.getId()));
                        try {
                            String followUp = geminiManager.generateFollowUp(
                                    questionDTO.getQuestion(),
                                    voiceText,
                                    StringUtils.defaultIfBlank(setting.getFollowUpStandards(), setting.getFollowUpPrompt())
                            );
                            if (StringUtils.isBlank(followUp)) {
                                followUp = buildDefaultFollowUpQuestion(questionDTO.getQuestion());
                            }
                            cacheFollowUp(followUpKey, studentId, assessmentId, questionIndex, followUp);
                            log.info("追问生成完成, studentId:{}, assessmentId:{}, questionIndex:{}",
                                    studentId, assessmentId, questionIndex);
                        } catch (Exception e) {
                            log.error("追问生成失败, studentId:{}, assessmentId:{}", studentId, assessmentId, e);
                            cacheFollowUp(followUpKey, studentId, assessmentId, questionIndex,
                                    buildDefaultFollowUpQuestion(questionDTO.getQuestion()));
                            WebsocketManager.sendTextMessage(studentId, WebSocketResponse.of("追问生成失败，将使用默认追问"));
                        }
                    } else {
                        String voiceText = getRealtimeVoiceContentOrFallback(studentId, voiceBytes);
                        questionAnswerService.update(Wrappers.lambdaUpdate(StudentAssessmentQuestionAnswerPO.class)
                                .set(StudentAssessmentQuestionAnswerPO::getAnswer, voiceText)
                                .eq(StudentAssessmentQuestionAnswerPO::getId, questionAnswerPO.getId()));
                    }
                } catch (Exception e) {
                    log.error("普通题回答处理失败, studentId:{}, assessmentId:{}", studentId, assessmentId, e);
                    WebsocketManager.sendTextMessage(studentId, WebSocketResponse.of("答题处理失败，将进入下一题"));
                } finally {
                    if (!directFollowUpSent.get()) {
                        WebsocketManager.sendTextMessage(studentId, WebSocketResponse.of("题目生成完成，请点击获取题目"));
                    }
                }
            });
        }
    }

    private String realtimeAnswerOrFallback(String realtimeAnswerText, byte[] voiceBytes) {
        if (StringUtils.isNotBlank(realtimeAnswerText)) {
            return realtimeAnswerText;
        }
        return getVoiceContent(voiceBytes);
    }

    private boolean tryGenerateAndSendRealtimeFollowUp(Integer studentId,
                                                       Integer assessmentId,
                                                       AssessmentSettingPO setting,
                                                       GeneratedQuestionDTO originalQuestion,
                                                       String followUpKey,
                                                       int questionIndex,
                                                       AtomicReference<String> answerTextRef) {
        AtomicBoolean sentToStudent = new AtomicBoolean(false);
        AtomicReference<String> followUpRef = new AtomicReference<>();
        WebsocketManager.startVoiceSending(studentId);
        try {
            Optional<RealtimeSpeechManager.RealtimeTurnResponse> response = realtimeSpeechManager.completeTurnAndStreamResponse(
                    studentId,
                    answerText -> {
                        answerTextRef.set(answerText);
                        return buildRealtimeFollowUpInstructions(originalQuestion.getQuestion(), answerText,
                                StringUtils.defaultIfBlank(setting.getFollowUpStandards(), setting.getFollowUpPrompt()));
                    },
                    chunk -> {
                        if (!WebsocketManager.isVoiceSendingInterrupted(studentId)) {
                            WebsocketManager.sendVoiceChunk(chunk, studentId);
                        }
                    },
                    transcript -> {
                        String followUp = normalizeFollowUpQuestion(transcript, originalQuestion.getQuestion());
                        followUpRef.set(followUp);
                        sendRealtimeFollowUpText(studentId, assessmentId, setting, followUpKey, questionIndex, followUp);
                        sentToStudent.set(true);
                    }
            );

            String followUp = response
                    .map(RealtimeSpeechManager.RealtimeTurnResponse::responseTranscript)
                    .filter(StringUtils::isNotBlank)
                    .map(text -> normalizeFollowUpQuestion(text, originalQuestion.getQuestion()))
                    .orElseGet(followUpRef::get);
            response.map(RealtimeSpeechManager.RealtimeTurnResponse::inputTranscript)
                    .filter(StringUtils::isNotBlank)
                    .ifPresent(answerTextRef::set);
            if (StringUtils.isBlank(followUp)) {
                return false;
            }
            if (!sentToStudent.get()) {
                sendRealtimeFollowUpText(studentId, assessmentId, setting, followUpKey, questionIndex, followUp);
                sentToStudent.set(true);
            }
            log.info("Realtime追问生成完成, studentId:{}, assessmentId:{}, questionIndex:{}, hasAudio:{}",
                    studentId, assessmentId, questionIndex, response.map(RealtimeSpeechManager.RealtimeTurnResponse::hasAudio).orElse(false));
            return true;
        } catch (Exception e) {
            log.warn("Realtime追问生成失败, studentId:{}, assessmentId:{}, error:{}",
                    studentId, assessmentId, e.getMessage());
            return false;
        } finally {
            WebsocketManager.sendVoiceEnd(studentId);
            WebsocketManager.finishVoiceSending(studentId);
        }
    }

    private void sendRealtimeFollowUpText(Integer studentId,
                                          Integer assessmentId,
                                          AssessmentSettingPO setting,
                                          String followUpKey,
                                          int questionIndex,
                                          String followUp) {
        cacheFollowUp(followUpKey, studentId, assessmentId, questionIndex, followUp);
        GeneratedQuestionDTO followDTO = new GeneratedQuestionDTO();
        followDTO.setQuestion(followUp);
        followDTO.setFollow(true);
        sendQuestionTextToStudent(followDTO, studentId, assessmentId, questionIndex, setting.getQuestionCount(),
                response -> WebsocketManager.sendTextMessage(studentId, response));
    }

    private String buildRealtimeFollowUpInstructions(String originalQuestion, String answerText, String followUpStandards) {
        return """
                你是正在进行大学综合答辩的AI面试官。请基于学生刚才的语音回答，直接生成一个追问问题，并用清晰自然的中文语音说出来。

                要求：
                1. 只输出追问问题本身，不要解释、不要编号、不要输出JSON或Markdown。
                2. 必须围绕原问题和学生回答继续深入，不要更换无关主题。
                3. 优先追问依据、实现细节、验证数据、关键技术、对比逻辑或学生回答中含糊的地方。
                4. 语言简洁明确，适合作为面试现场的一句话追问。

                【原问题】
                %s

                【学生回答自动转写】
                %s

                【追问标准】
                %s
                """.formatted(
                StringUtils.defaultIfBlank(originalQuestion, "无"),
                StringUtils.defaultIfBlank(answerText, "以刚才的语音回答为准"),
                StringUtils.defaultIfBlank(followUpStandards, "围绕回答继续深入追问")
        );
    }

    private String normalizeFollowUpQuestion(String text, String originalQuestion) {
        String followUp = StringUtils.trimToEmpty(text);
        if (followUp.startsWith("```")) {
            int startIdx = followUp.indexOf('\n') + 1;
            int endIdx = followUp.lastIndexOf("```");
            if (startIdx > 0 && endIdx > startIdx) {
                followUp = followUp.substring(startIdx, endIdx).trim();
            }
        }
        followUp = StringUtils.strip(followUp, "\"'“”‘’");
        return StringUtils.defaultIfBlank(followUp, buildDefaultFollowUpQuestion(originalQuestion));
    }

    private String buildDefaultFollowUpQuestion(String originalQuestion) {
        if (StringUtils.isBlank(originalQuestion)) {
            return "请进一步补充说明你的依据、实现细节和验证结果。";
        }
        return "围绕刚才的问题，请进一步补充说明你的依据、实现细节和验证结果。";
    }

    private void startAnswer(Integer studentId, Consumer<WebSocketResponse> consumer, int assessmentId) {
        AssessmentSettingPO setting = settingService.getById(assessmentId);
        Integer answerTime = setting.getAnswerTime();
        VOICE_MAP.put(studentId, new ByteArrayOutputStream(answerTime * 3 * 8192));
        realtimeSpeechManager.startTurn(studentId);
        consumer.accept(WebSocketResponse.ofLast("开始答题"));
    }

    private void endDefense(Integer studentId, Consumer<WebSocketResponse> consumer, int assessmentId) {
        ByteArrayOutputStream byteArrayOutputStream = VOICE_MAP.get(studentId);
        if (byteArrayOutputStream == null) {
            consumer.accept(WebSocketResponse.ofLast("未找到录音数据"));
            return;
        }
        if (!hasValidAudio(byteArrayOutputStream)) {
            VOICE_MAP.remove(studentId);
            consumer.accept(WebSocketResponse.ofError("未检测到有效录音，请重新答辩"));
            return;
        }
        byte[] bytes = addWavHeader(byteArrayOutputStream.toByteArray());
        String voiceUrl = ossManger.uploadVoice(bytes);
        recordService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                .set(StudentAssessmentRecordPO::getDefenseVoice, voiceUrl)
                .set(StudentAssessmentRecordPO::getEndDefenseTime, LocalDateTime.now())
                .eq(StudentAssessmentRecordPO::getStudentId, studentId)
                .eq(StudentAssessmentRecordPO::getAssessmentId, assessmentId));
        consumer.accept(WebSocketResponse.ofLast("结束答辩"));
        // 异步翻译
        threadPoolExecutor.execute(() -> {
            String defenseContent = getRealtimeVoiceContentOrFallback(studentId, bytes);
            recordService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                    .set(StudentAssessmentRecordPO::getDefenseContent, defenseContent)
                    .eq(StudentAssessmentRecordPO::getStudentId, studentId)
                    .eq(StudentAssessmentRecordPO::getAssessmentId, assessmentId));
        });
        VOICE_MAP.remove(studentId);
    }

    private byte[] addWavHeader(byte[] pcmData) {
        ByteBuffer buffer = ByteBuffer.allocate(44 + pcmData.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // WAV头（16kHz, 16bit, 单声道）
        buffer.put("RIFF".getBytes());
        buffer.putInt(36 + pcmData.length);
        buffer.put("WAVE".getBytes());
        buffer.put("fmt ".getBytes());
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(16000);
        buffer.putInt(32000);
        buffer.putShort((short) 1);
        buffer.putShort((short) 16);
        buffer.put("data".getBytes());
        buffer.putInt(pcmData.length);
        buffer.put(pcmData);

        return buffer.array();
    }

    private void startDefense(Integer studentId, Consumer<WebSocketResponse> consumer, int assessmentId) {
        AssessmentSettingPO setting = settingService.getById(assessmentId);
        Integer duration = setting.getDuration();
        VOICE_MAP.put(studentId, new ByteArrayOutputStream(duration * 3 * 8192));
        realtimeSpeechManager.startTurn(studentId);

        Optional<StudentAssessmentRecordPO> recordOp = recordService.findByStudentIdAndAssessmentId(studentId, assessmentId);
        LocalDateTime now = LocalDateTime.now();
        if (recordOp.isPresent()) {
            recordService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                    .set(StudentAssessmentRecordPO::getStartDefenseTime, now)
                    .set(StudentAssessmentRecordPO::getEndDefenseTime, null)
                    .set(StudentAssessmentRecordPO::getDefenseVoice, "")
                    .set(StudentAssessmentRecordPO::getDefenseContent, null)
                    .set(StudentAssessmentRecordPO::getDefenseResult, null)
                    .set(StudentAssessmentRecordPO::getScore, -1)
                    .set(StudentAssessmentRecordPO::getCheckScore, 0)
                    .set(StudentAssessmentRecordPO::getState, 0)
                    .set(StudentAssessmentRecordPO::getReAnalysis, false)
                    .eq(StudentAssessmentRecordPO::getStudentId, studentId)
                    .eq(StudentAssessmentRecordPO::getAssessmentId, assessmentId));
        } else {
            StudentAssessmentRecordPO record = StudentAssessmentRecordPO.builder()
                    .assessmentId(assessmentId)
                    .studentId(studentId)
                    .startDefenseTime(now)
                    .score(-1)
                    .state(0)
                    .reAnalysis(false)
                    .build();
            recordService.save(record);
        }

        // 考核中
        studentAppointmentService.update(Wrappers.lambdaUpdate(StudentAssessmentAppointmentPO.class)
                .set(StudentAssessmentAppointmentPO::getState, 1)
                .eq(StudentAssessmentAppointmentPO::getAssessmentId, assessmentId)
                .eq(StudentAssessmentAppointmentPO::getStudentId, studentId));
        consumer.accept(WebSocketResponse.ofLast("开始答辩"));
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean uploadDefenseFile(StudentDefenseFileUploadCmd uploadCmd, Integer studentId) {
        Integer assessmentId = uploadCmd.getAssessmentId();
        String defenseFile = JSONUtil.toJsonStr(uploadCmd.getFile());
        Optional<StudentAssessmentRecordPO> recordOp = recordService.findByStudentIdAndAssessmentId(studentId, assessmentId);
        if (recordOp.isPresent()) {
            return recordService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                    .set(StudentAssessmentRecordPO::getDefenseFile, defenseFile)
                    .eq(StudentAssessmentRecordPO::getStudentId, studentId)
                    .eq(StudentAssessmentRecordPO::getAssessmentId, assessmentId));
        }
        StudentAssessmentRecordPO record = recordOp.orElseGet(() -> StudentAssessmentRecordPO.builder()
                .assessmentId(assessmentId)
                .studentId(studentId)
                .defenseFile(defenseFile)
                .defenseVoice("")
                .score(-1)
                .checkScore(0)
                .state(0)
                .reAnalysis(false)
                .build());
        return recordService.saveOrUpdate(record);
    }

    public void appendVoice(ByteBuffer payload, Integer studentId) throws IOException {
        ByteBuffer copy = payload.asReadOnlyBuffer();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        VOICE_MAP.computeIfAbsent(studentId, k -> new ByteArrayOutputStream()).write(bytes);
    }

    public StudentAssessmentDetailDTO assessmentDetailByRecordId(Integer id) {
        StudentAssessmentRecordPO record = recordService.getById(id);
        AssessmentSettingPO setting = settingService.findById(record.getAssessmentId())
                .orElseThrow(() -> new BusinessException(ErrorCodeEnums.ASSESSMENT_SETTING_NOT_FOUND));
        UserPO student = userService.findById(record.getStudentId())
                .orElseThrow(() -> new BusinessException(ErrorCodeEnums.USER_NOT_FOUND));

        String location = Optional.ofNullable(studentAppointmentService.getByStudentIdAndAssessmentId(record.getStudentId(), record.getAssessmentId())).map(StudentAssessmentAppointmentPO::getLocation).orElse(null);
        return getStudentAssessmentDetailDTO(record, student, setting, location);
    }

    public StudentAssessmentDetailDTO assessmentDetail(Integer id) {
        int studentId = StpUtil.getLoginIdAsInt();
        AssessmentSettingPO setting = settingService.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnums.ASSESSMENT_SETTING_NOT_FOUND));

        StudentAssessmentRecordPO record = Optional.ofNullable(recordService.getOne(Wrappers.lambdaQuery(StudentAssessmentRecordPO.class)
                .eq(StudentAssessmentRecordPO::getAssessmentId, id)
                .eq(StudentAssessmentRecordPO::getStudentId, studentId)))
                .orElseThrow(() -> new BusinessException(ErrorCodeEnums.STUDENT_ASSESSMENT_NOT_FOUND));
        UserPO student = userService.findById(record.getStudentId())
                .orElseThrow(() -> new BusinessException(ErrorCodeEnums.USER_NOT_FOUND));
        String location = Optional.ofNullable(studentAppointmentService.getByStudentIdAndAssessmentId(studentId, id)).map(StudentAssessmentAppointmentPO::getLocation).orElse(null);

        return getStudentAssessmentDetailDTO(record, student, setting, location);
    }

    private StudentAssessmentDetailDTO getStudentAssessmentDetailDTO(StudentAssessmentRecordPO record,
                                                                     UserPO student,
                                                                     AssessmentSettingPO setting,
                                                                     String location) {
        Map<Integer, StudentAssessmentQuestionAnswerPO> quesitonAnswerIdMap = questionAnswerService.listByAssessmentIdAndStudentId(record.getAssessmentId(), record.getStudentId()).stream()
                .collect(Collectors.toMap(StudentAssessmentQuestionAnswerPO::getId, item -> item));
        String defenseResult = record.getDefenseResult();
        if (StringUtils.isNotBlank(defenseResult)) {
            AssessmentEvaluationDTO evaluation = JSONUtil.toBean(defenseResult, AssessmentEvaluationDTO.class);
            evaluation.fillEmptyValue(quesitonAnswerIdMap, record);
            Integer checkScore = record.getCheckScore();
            List<ValueDTO> value = evaluation.getValue();
            int totalScore = resolveDisplayScore(checkScore, value);
            DefenseDTO defense = evaluation.getDefense();
            if (defense != null) {
                defense.setDefenseAnswer(record.getDefenseContent());
                defense.setDefenseAnswerFile(Optional.ofNullable(record.getDefenseVoice())
                        .map(ossManger::getSignedUrl)
                        .orElse(null));
            }
            List<AnswerEvaluationDTO> result = evaluation.getResult();
            if (CollectionUtils.isNotEmpty(result)) {
                for (AnswerEvaluationDTO answerEvaluationDTO : result) {
                    StudentAssessmentQuestionAnswerPO matchAnswer = quesitonAnswerIdMap.get(answerEvaluationDTO.getId());
                    if (matchAnswer == null) {
                        continue;
                    }
                    answerEvaluationDTO.setQuestion(matchAnswer.getQuestion());
                    answerEvaluationDTO.setAnswer(matchAnswer.getAnswer());
                    answerEvaluationDTO.setAnswerFile(ossManger.getSignedUrl(matchAnswer.getAnswerVoice()));
                    answerEvaluationDTO.setFollowQuestion(matchAnswer.getFollowQuestion());
                    answerEvaluationDTO.setFollowAnswer(matchAnswer.getFollowAnswer());
                    Optional.ofNullable(matchAnswer.getFollowAnswerVoice())
                            .ifPresent(item -> answerEvaluationDTO.setFollowAnswerFile(ossManger.getSignedUrl(item)));
                }
            }
            return StudentAssessmentDetailDTO.builder()
                    .id(record.getId())
                    .studentName(student.getName())
                    .passScore(setting.getPassScore())
                    .score(totalScore)
                    .totalScore(setting.getTotalScore())
                    .result(result)
                    .analysis(evaluation.getAnalysis())
                    .summary(evaluation.getSummary())
                    .value(value)
                    .defense(defense)
                    .showResult(setting.getShowResult())
                    .state(record.getState())
                    .reAnalysis(record.getReAnalysis())
                    .location(location)
                    .build();
        }
        return StudentAssessmentDetailDTO.builder()
                .id(record.getId())
                .studentName(student.getName())
                .showResult(setting.getShowResult())
                .state(record.getState())
                .reAnalysis(record.getReAnalysis())
                .location(location)
                .build();
    }

    private String buildGenerateQuestionSystemInstruction() {
        return """
                    你是一位严谨的大学答辩导师，基于pdf文件给学生进行出题。
                
                    【角色与任务】：
                     作为答辩导师，你需要根据知识背景和以下条件，设计能够有效评估学生理解深度和应用能力的问题。你的问题应当具有学术严谨性，同时符合教育评估的最佳实践。
                
                    【知识背景】：
                     所有PDF格式的文件内容都是此次考核的知识背景材料。这些材料仅用于帮助你理解考核主题、相关概念和上下文环境。你应当充分理解这些背景知识，但生成问题时不能直接复制背景材料中的内容，而应当基于背景知识设计原创性问题。
                
                    【绝对规则与约束】：
                     1. 生成的所有问题必须严格基于已有对话内容和提供的知识背景，严禁虚构或添加学生未提及的内容
                     2. 问题应当具有明确的评估目标，能够检验学生对核心概念的理解和应用能力
                     3. 避免提出模糊不清或过于宽泛的问题，确保问题具有可评估性和针对性
                     4. 问题的难度应当适合大学生的学术水平，既不能过于简单也不能超出合理范围
                
                    【信息说明】：
                     1. 是否首次提问：表示是否是首次出题
                     2. 历史提问问题：这是之前已经提出过的问题列表，你需要确保新生成的问题不与这些问题重复，同时考虑问题的递进性和多样性
                     3. 最近一次问答：这是上一次的提问和学生的回答，你需要分析学生的回答质量，决定是否需要追问或转换方向
                     4. 是否追问：判断此次提问是直接生成一个全新的问题还是对最近一次问答进行追问的标记
                     5. 追问标准：判断最近一次问答是否需要进行追问的标准
                     6. 答辩内容：出题的依据之一，如果没有则忽略
                     7. 是否使用题库：判断是否使用题库的标记
                     8. 题库：准备的题库，题库中每个编号代表一个题目
                
                    【出题要求】
                     1. 避免重复：确保新问题与“历史提问问题”中的内容不重复，包括语义重复。
                     2. 递进性与多样性：
                       若为首次提问，问题应为基础性、引导性问题；
                       若非首次，应根据学习进度提升难度或转换角度（如从记忆→理解→应用→分析）。
                     3. 追问逻辑（仅当 是否追问 = 是 时适用）：
                       根据“追问标准”和“学生回答”判断其理解程度；
                       追问应聚焦于澄清误解、深化思考或引导反思，而非简单重复。
                     4. 题库使用（仅当 是否使用题库 = 是 时适用）：
                       从题库中随机选择一个题目，必须严格遵守以下规则：
                       4.1. 随机选择：从题库中完全随机选择题目，不能按照任何固定顺序（如编号顺序、难度顺序等）
                       4.2. 避免模式：确保选择模式无规律可循，不要总是选择开头、结尾或特定位置的题目
                       4.3. 考虑历史：确保新问题与“历史提问问题”中的内容不重复
                     5. 语言风格：问题应清晰、简洁、具有启发性，适合当前学习者水平
                
                    【问题质量保障】：
                     1. 确保每个问题都有明确的评估目标和预期回答要点
                     2. 避免提出复合问题(一个问题中包含多个独立问题)
                     3. 考虑问题的认知负荷，确保不会因问题表述复杂而影响对学生能力的准确评估
                     4. 对于应用型和评估型问题，确保提供足够的上下文但不过度引导
                     5. 适当使用Bloom分类法中的不同认知层次，确保问题覆盖知识、理解、应用、分析、综合和评估等多个层次
                
                    【生成模板与输出格式】：
                     你的输出应当严格遵循以下JSON格式：
                     {
                       "follow": boolean, // 表示是否是追问问题
                       "userQuestionBank": boolean //表示是否使用了题库
                       "question": string // 生成的问题内容，应当清晰、简洁且直接
                     }
                
                     注意事项：
                     1. follow字段必须为布尔值，true表示追问，false表示新问题
                     2. question字段应当只包含问题本身，不要添加任何解释、说明或额外文本
                     3. 问题应当用中文表述，除非考核内容本身要求使用其他语言
                     4. 问题长度应当适中，既不能过于简短导致不明确，也不能过于冗长影响理解
                
                    【学术伦理考虑】：
                     1. 确保所有问题尊重学术诚信原则，不鼓励或暗示任何形式的学术不端行为
                     2. 问题设计应当公平、无偏见，不会因文化背景、性别或其他非学术因素而对某些学生群体不利
                     3. 考虑问题的可访问性，避免使用可能妨碍某些学生理解的晦涩术语或复杂句式
                
                    【最终审核】：
                    在生成问题后，请自我审核以下方面：
                    1. 问题是否与考核内容直接相关
                    2. 问题是否清晰无歧义
                    3. 问题难度是否适合大学生的学术水平
                    4. 问题是否避免了重复且具有评估价值
                    5. 问题格式是否符合输出要求
                
                    请确保你生成的问题符合上述所有要求，这将直接影响考核的质量和公平性。
                """;
    }

    // 构建系统指令（稳定的部分）
    private String buildSummarySystemInstruction(AssessmentSettingPO setting) {
        if (setting.getDefense() && setting.getQuestion()) {
            return String.format(PromptTemplateConstant.DEFENSE_AND_QUESTION_ANALYSIS_TEMPLATE_PROMPT, setting.getAssessmentCriteria(), "空");
        } else if (setting.getDefense()) {
            return String.format(PromptTemplateConstant.DEFENSE_ANALYSIS_TEMPLATE_PROMPT, setting.getAssessmentCriteria(), "空");
        } else {
            return String.format(PromptTemplateConstant.QUESTION_ANALYSIS_TEMPLATE_PROMPT, setting.getAssessmentCriteria(), "空");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean studentAppointment(Integer studentId, StudentAppointmentCmd appointmentCmd) {
        AssessmentSettingPO assessmentSettingPO = settingService.findByIdLock(appointmentCmd.getAssessmentId()).orElseThrow(() -> new BusinessException(ErrorCodeEnums.ASSESSMENT_SETTING_NOT_FOUND));
        AssessmentAppointmentSettingPO appointmentSettingPO = assessmentSettingPO.findAppointmentSetting(appointmentCmd.getTimePeriod());
        if (appointmentSettingPO == null) {
            throw new BusinessException(ErrorCodeEnums.ASSESSMENT_APPOINTMENT_SETTING_NOT_FOUND);
        }
        LocalDateTime timePeriod = appointmentSettingPO.getTimePeriod();
        if (timePeriod.isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCodeEnums.ASSESSMENT_SETTING_NOT_CAN_APPOINTMENT);
        }
        StudentAssessmentAppointmentPO studentExistAppoint = studentAppointmentService.getOne(Wrappers.lambdaQuery(StudentAssessmentAppointmentPO.class)
                .eq(StudentAssessmentAppointmentPO::getStudentId, studentId)
                .eq(StudentAssessmentAppointmentPO::getAssessmentId, assessmentSettingPO.getId()));
        if (studentExistAppoint != null) {
            if (Objects.equals(studentExistAppoint.getState(), APPOINTMENT_MISSED)
                    && Objects.equals(assessmentSettingPO.getAssessmentFailPunish(), true)) {
                LocalDateTime rescheduleAppointTime = assessmentSettingPO.getRescheduleAppointTime();
                if (rescheduleAppointTime == null) {
                    throw new BusinessException(ErrorCodeEnums.UNABLE_APPOINT);
                }
                if (rescheduleAppointTime.isAfter(LocalDateTime.now())) {
                    throw new BusinessException(ErrorCodeEnums.UNABLE_APPOINT);
                }
            }
        }
        LambdaQueryWrapper<StudentAssessmentAppointmentPO> countWrapper = Wrappers.lambdaQuery(StudentAssessmentAppointmentPO.class)
                .eq(StudentAssessmentAppointmentPO::getAssessmentId, assessmentSettingPO.getId())
                .eq(StudentAssessmentAppointmentPO::getTimePeriod, timePeriod)
                .and(query -> query.in(StudentAssessmentAppointmentPO::getState, APPOINTMENT_CONFIRMED, APPOINTMENT_IN_ASSESSMENT)
                        .or()
                        .isNull(StudentAssessmentAppointmentPO::getState));
        if (studentExistAppoint != null && studentExistAppoint.getId() != null) {
            countWrapper.ne(StudentAssessmentAppointmentPO::getId, studentExistAppoint.getId());
        }
        long appointmentCount = studentAppointmentService.count(countWrapper);
        // 统计是否已经预约满了
        if (appointmentCount >= appointmentSettingPO.getParticipantLimit()) {
            throw new BusinessException(ErrorCodeEnums.ASSESSMENT_SETTING_APPOINTMENT_LIMIT_EXCEED);
        }

        // 判断是否已经预约
        if (studentExistAppoint != null) {
            if (!Objects.equals(studentExistAppoint.getState(), 2) && !Objects.equals(studentExistAppoint.getState(), 4) && !Objects.equals(studentExistAppoint.getState(), APPOINTMENT_MISSED)) {
                throw new BusinessException(ErrorCodeEnums.ASSESSMENT_SETTING_APPOINTMENT_EXIST);
            }
            studentExistAppoint.setAppointmentTime(LocalDateTime.now());
            studentExistAppoint.setState(APPOINTMENT_CONFIRMED);
            studentExistAppoint.setPunishState(-1);
            studentExistAppoint.setTimePeriod(timePeriod);
            studentAppointmentService.updateById(studentExistAppoint);
            return true;
        }
        // 创建预约信息
        StudentAssessmentAppointmentPO assessmentAppointmentPO = new StudentAssessmentAppointmentPO();
        assessmentAppointmentPO.setAppointmentTime(LocalDateTime.now());
        assessmentAppointmentPO.setStudentId(studentId);
        assessmentAppointmentPO.setAssessmentId(appointmentCmd.getAssessmentId());
        assessmentAppointmentPO.setTimePeriod(timePeriod);
        assessmentAppointmentPO.setState(APPOINTMENT_CONFIRMED);
        assessmentAppointmentPO.setPunishState(-1);
        return studentAppointmentService.save(assessmentAppointmentPO);
    }

    public Boolean cancelAppointment(Integer studentId, Integer assessmentId) {
        return studentAppointmentService.remove(Wrappers.lambdaQuery(StudentAssessmentAppointmentPO.class)
                .eq(StudentAssessmentAppointmentPO::getStudentId, studentId)
                .eq(StudentAssessmentAppointmentPO::getAssessmentId, assessmentId));
    }
}
