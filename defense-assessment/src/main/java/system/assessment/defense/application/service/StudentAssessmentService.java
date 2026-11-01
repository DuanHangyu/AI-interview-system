package system.assessment.defense.application.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import kotlin.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import system.assessment.defense.application.dto.*;
import system.assessment.defense.application.dto.front.StudentAppointmentCmd;
import system.assessment.defense.application.task.TaskQueue;
import system.assessment.defense.application.task.TaskTypes;
import system.assessment.defense.domain.entity.StudentSummary;
import system.assessment.defense.infrastructure.emuns.ErrorCodeEnums;
import system.assessment.defense.infrastructure.exception.BusinessException;
import system.assessment.defense.infrastructure.repository.dao.po.*;
import system.assessment.defense.infrastructure.repository.dao.service.*;
import system.assessment.defense.infrastructure.storage.FileStorage;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @USER taoHouChao
 * @DATE 20:09 2025/8/14
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentAssessmentService {

    private static final int APPOINTMENT_CONFIRMED = 0;
    private static final int APPOINTMENT_IN_ASSESSMENT = 1;
    private static final int APPOINTMENT_MISSED = 3;

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

    static boolean isDefenseFinishedForCurrentTodo(StudentAssessmentRecordPO record) {
        if (record == null || record.getEndDefenseTime() == null) {
            return false;
        }
        Integer state = record.getState();
        return state == null || Objects.equals(state, 0);
    }

    private final StudentAssessmentRecordService recordService;

    private final UserService userService;

    private final AssessmentSettingService settingService;

    private final AssessmentAppointmentSettingService appointmentSettingService;

    private final FileStorage fileStorage;

    private final StudentAssessmentQuestionAnswerService questionAnswerService;

    private final StudentAssessmentAppointmentService studentAppointmentService;

    private final TaskQueue taskQueue;


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

    /**
     * 手动重新评分（评分失败或需要重新生成评语时由教师触发），由后台任务异步执行。
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
        if (taskQueue.isRunning(TaskTypes.ANALYSIS, recordId)) {
            throw new BusinessException(132, "该记录正在分析中，请稍后再试");
        }
        recordService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                .set(StudentAssessmentRecordPO::getState, 2)
                .set(StudentAssessmentRecordPO::getReAnalysis, true)
                .set(StudentAssessmentRecordPO::getUpdateTime, LocalDateTime.now())
                .eq(StudentAssessmentRecordPO::getId, recordId));
        taskQueue.enqueue(TaskTypes.ANALYSIS, recordId);
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

            for (StudentTodoDefenseDTO resultRecord : resultRecords) {
                Optional.ofNullable(assessmentIdMap.get(resultRecord.getId()))
                        .ifPresent((value) -> resultRecord.setFinishDefense(isDefenseFinishedForCurrentTodo(value)));
                Optional.ofNullable(assessmentAppointmentTimeMap.get(resultRecord.getId()))
                        .ifPresent(iem -> {
                            resultRecord.setTimePeriod(iem.getTimePeriod());
                            resultRecord.setLocation(iem.getLocation());
                        });
            }
        }
        return resultPage;
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
                        .map(fileStorage::signedUrl)
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
                    answerEvaluationDTO.setAnswerFile(fileStorage.signedUrl(matchAnswer.getAnswerVoice()));
                    answerEvaluationDTO.setFollowQuestion(matchAnswer.getFollowQuestion());
                    answerEvaluationDTO.setFollowAnswer(matchAnswer.getFollowAnswer());
                    Optional.ofNullable(matchAnswer.getFollowAnswerVoice())
                            .ifPresent(item -> answerEvaluationDTO.setFollowAnswerFile(fileStorage.signedUrl(item)));
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
