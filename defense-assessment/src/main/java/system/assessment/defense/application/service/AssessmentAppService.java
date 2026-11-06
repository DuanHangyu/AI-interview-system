package system.assessment.defense.application.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import system.assessment.defense.application.dto.*;
import system.assessment.defense.application.dto.backed.*;
import system.assessment.defense.application.service.interview.QuestionBankService;
import system.assessment.defense.domain.event.SettingCreateOrUpdateEvent;
import system.assessment.defense.infrastructure.emuns.ErrorCodeEnums;
import system.assessment.defense.infrastructure.exception.BusinessException;
import system.assessment.defense.infrastructure.repository.dao.po.*;
import system.assessment.defense.infrastructure.repository.dao.service.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @USER taoHouChao
 * @DATE 20:20 2025/8/7
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentAppService {

    private static final Object OBJECT = new Object();

    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(4, 10, 1000, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1000), new ThreadPoolExecutor.CallerRunsPolicy());

    private final UserService userService;

    private final AssessmentSettingService assessmentSettingService;

    private final StudentAssessmentRecordService recordService;

    private final StudentAssessmentQuestionAnswerService questionAnswerService;

    private final AssessmentStudentRelationService assessmentStudentRelationService;

    private final AssessmentInviteService assessmentInviteService;

    private final AssessmentAppointmentSettingService appointmentSettingService;

    private final StudentAssessmentAppointmentService studentAssessmentAppointmentService;

    private final QuestionBankService questionBankService;

    private final ApplicationEventPublisher publisher;

    @Transactional(rollbackFor = Exception.class)
    public Boolean createAssessmentSettings(AssessmentSettingsCreateCmd createCmd) {
        int teacherId = StpUtil.getLoginIdAsInt();
        createCmd.check();
        AssessmentSettingPO assessmentSettingPo = createCmd.toPO();
        assessmentSettingService.save(assessmentSettingPo);
        questionBankService.saveQuestions(assessmentSettingPo.getId(), createCmd.getQuestions());

        List<Integer> inviteTeachers = createCmd.getInviteTeachers();
        List<SchoolClassStudentSaveCmd> schoolClassList = createCmd.getSchoolClassList();
        List<Integer> studentIds = createCmd.getStudentIds();
        saveRelationStudent(inviteTeachers, assessmentSettingPo.getId(), teacherId, studentIds, schoolClassList);
        publisher.publishEvent(new SettingCreateOrUpdateEvent(OBJECT, assessmentSettingPo));
        return true;
    }

    private void saveRelationStudent(List<Integer> inviteTeachers,
                                     Integer settingId,
                                     Integer teacherId,
                                     List<Integer> studentIds,
                                     List<SchoolClassStudentSaveCmd> schoolClassList) {
        // 校验是否有相同的学生id
        checkSameStudent(studentIds, schoolClassList);
        if (CollectionUtils.isNotEmpty(inviteTeachers)) {
            List<AssessmentInvitePO> list = inviteTeachers.stream()
                    .map(item -> AssessmentInvitePO.builder()
                            .assessmentId(settingId)
                            .inviteeTeacherId(item)
                            .InviterTeacherId(teacherId)
                            .build())
                    .toList();
            assessmentInviteService.saveBatch(list);
        }

        List<AssessmentStudentRelationPO> relations = new ArrayList<>(100);
        if (CollectionUtils.isNotEmpty(studentIds)) {
            List<AssessmentStudentRelationPO> relationList = studentIds.stream()
                    .map(item -> AssessmentStudentRelationPO.builder()
                            .assessmentId(settingId)
                            .studentId(item)
                            .relationType(0)
                            .build())
                    .toList();
            relations.addAll(relationList);
        }

        if (CollectionUtils.isNotEmpty(schoolClassList)) {
            for (SchoolClassStudentSaveCmd schoolClassStudentDTO : schoolClassList) {
                List<Integer> students = schoolClassStudentDTO.getStudents();
                relations.addAll(students.stream()
                        .map(item -> AssessmentStudentRelationPO.builder()
                                .assessmentId(settingId)
                                .studentId(item)
                                .relationType(1)
                                .build())
                        .toList());
            }
        }
        assessmentStudentRelationService.saveBatch(relations);
    }

    private static void checkSameStudent(List<Integer> studentIds, List<SchoolClassStudentSaveCmd> schoolClassList) {
        if (CollectionUtils.isNotEmpty(studentIds) && CollectionUtils.isNotEmpty(schoolClassList)) {
            for (SchoolClassStudentSaveCmd schoolClassStudentDTO : schoolClassList) {
                List<Integer> students = schoolClassStudentDTO.getStudents();
                for (Integer student : students) {
                    if (studentIds.contains(student)) {
                        throw new BusinessException(ErrorCodeEnums.IMPORT_STUDENT_EXIST);
                    }
                }
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean modifyAssessmentSettings(AssessmentSettingsModifyCmd modifyCmd) {
        int teacherId = StpUtil.getLoginIdAsInt();
        modifyCmd.check();
        Integer id = modifyCmd.getId();
        AssessmentSettingPO assessmentSetting = assessmentSettingService.getById(id);
        if (assessmentSetting == null) {
            throw new BusinessException(ErrorCodeEnums.ASSESSMENT_SETTING_NOT_FOUND);
        }
        AssessmentSettingPO participatingStudents = modifyCmd.toPO();
        assessmentStudentRelationService.remove(Wrappers.lambdaQuery(AssessmentStudentRelationPO.class)
                .eq(AssessmentStudentRelationPO::getAssessmentId, assessmentSetting.getId()));
        assessmentInviteService.remove(Wrappers.lambdaQuery(AssessmentInvitePO.class)
                .eq(AssessmentInvitePO::getAssessmentId, assessmentSetting.getId()));
        saveRelationStudent(modifyCmd.getInviteTeachers(), modifyCmd.getId(), teacherId, modifyCmd.getStudentIds(), modifyCmd.getSchoolClassList());
        // 是否分场次由考场页的场次设置决定（ExamSessionService），编辑面试时不改动，避免用打开页面时的旧值覆盖或删除场次
        participatingStudents.setNeedAppoint(null);
        questionBankService.saveQuestions(id, modifyCmd.getQuestions());
        publisher.publishEvent(new SettingCreateOrUpdateEvent(OBJECT, participatingStudents));
        return assessmentSettingService.updateById(participatingStudents);
    }

    /**
     * 分页查询考核设置列表，并封装为 DTO 返回。
     * <p>
     * 该方法会根据分页参数和主题关键词查询考核设置信息，并同时查询每个考核设置关联的学生信息，
     * 最终将数据封装为 {@link AssessmentSettingsDTO} 并返回分页结果。
     *
     * @param pagingQuery 分页查询参数，包含页码、页大小和查询关键词等信息
     * @return 分页结果，包含考核设置信息及其参与学生列表
     */
    @Transactional(readOnly = true)
    public IPage<AssessmentSettingsDTO> list(Integer teacherId, AssessmentSettingPagingQuery pagingQuery) throws Exception {
        UserPO user = userService.getById(teacherId);
        IPage<AssessmentSettingPO> poPage;
        // 管理员
        if (Objects.equals(user.getType(), 2)) {
            poPage = assessmentSettingService.page(new Page<>(pagingQuery.getPage(), pagingQuery.getSize()),
                    Wrappers.lambdaQuery(AssessmentSettingPO.class)
                            .like(StrUtil.isNotBlank(pagingQuery.getTheme()), AssessmentSettingPO::getTheme, pagingQuery.getTheme())
                            .orderByDesc(AssessmentSettingPO::getId));
        } else {
            List<Integer> assessmentId = assessmentInviteService.list(Wrappers.lambdaQuery(AssessmentInvitePO.class)
                            .eq(AssessmentInvitePO::getInviteeTeacherId, teacherId))
                    .stream()
                    .map(AssessmentInvitePO::getAssessmentId)
                    .toList();
            // 查询考核设置分页数据，支持按主题模糊查询并按 ID 倒序排列
            poPage = assessmentSettingService.page(new Page<>(pagingQuery.getPage(), pagingQuery.getSize()),
                    Wrappers.lambdaQuery(AssessmentSettingPO.class)
                            .and(query -> query.eq(AssessmentSettingPO::getCreateId, teacherId)
                                    .like(StrUtil.isNotBlank(pagingQuery.getTheme()), AssessmentSettingPO::getTheme, pagingQuery.getTheme()))
                            .or(CollectionUtils.isNotEmpty(assessmentId), query -> query.in(AssessmentSettingPO::getId, assessmentId))
                            .orderByDesc(AssessmentSettingPO::getId));
        }
        List<AssessmentSettingPO> records = poPage.getRecords();
        if (CollectionUtils.isNotEmpty(records)) {
            // 收集所有考核设置的 ID，用于查询关联的学生关系
            List<Integer> assessmentIds = records.stream().map(AssessmentSettingPO::getId).toList();
            CompletableFuture<Map<Integer, List<AssessmentStudentRelationPO>>> assessmentIdMapFuture = CompletableFuture.supplyAsync(() -> assessmentStudentRelationService.list(Wrappers.lambdaQuery(AssessmentStudentRelationPO.class)
                            .in(AssessmentStudentRelationPO::getAssessmentId, assessmentIds)).stream()
                    .collect(Collectors.groupingBy(AssessmentStudentRelationPO::getAssessmentId)), EXECUTOR);

            CompletableFuture<Map<Integer, List<AssessmentInvitePO>>> AssessmentInvitePOFuture = CompletableFuture.supplyAsync(() -> assessmentInviteService.list(Wrappers.lambdaQuery(AssessmentInvitePO.class)
                            .in(AssessmentInvitePO::getAssessmentId, assessmentIds)).stream()
                    .collect(Collectors.groupingBy(AssessmentInvitePO::getAssessmentId)), EXECUTOR);

            CompletableFuture<Map<Integer, List<AssessmentAppointmentSettingPO>>> appointMapFuture = CompletableFuture.supplyAsync(() -> appointmentSettingService.list(Wrappers.lambdaQuery(AssessmentAppointmentSettingPO.class)
                            .in(AssessmentAppointmentSettingPO::getAssessmentId, assessmentIds)).stream()
                    .collect(Collectors.groupingBy(AssessmentAppointmentSettingPO::getAssessmentId)), EXECUTOR);

            CompletableFuture<Map<Integer, UserPO>> studentMapFuture = CompletableFuture.supplyAsync(() -> userService.list().stream()
                    .collect(Collectors.toMap(UserPO::getId, item -> item)), EXECUTOR);

            CompletableFuture.allOf(assessmentIdMapFuture, AssessmentInvitePOFuture, appointMapFuture, studentMapFuture).join();
            // 按考核设置 ID 分组学生关系数据
            Map<Integer, List<AssessmentStudentRelationPO>> assessmentIdMap = assessmentIdMapFuture.get();
            Map<Integer, List<AssessmentInvitePO>> assessmentInviteIdMap = AssessmentInvitePOFuture.get();
            Map<Integer, List<AssessmentAppointmentSettingPO>> appointMap = appointMapFuture.get();

            // 存储学生信息的映射表，用于后续快速查找
            Map<Integer, UserPO> studentMap = studentMapFuture.get();

            Map<Integer, List<QuestionItemDTO>> questionMap = questionBankService.listQuestions(
                    records.stream().map(AssessmentSettingPO::getId).toList());

            // 转换 PO 为 DTO，并填充参与学生信息
            return poPage.convert(item -> {
                AssessmentSettingsDTO settingDTO = BeanUtil.copyProperties(item, AssessmentSettingsDTO.class, "participatingStudents", "assessmentFiles");
                settingDTO.setQuestions(questionMap.getOrDefault(item.getId(), List.of()));
                String assessmentFiles = item.getAssessmentFiles();
                if (StrUtil.isNotBlank(assessmentFiles)) {
                    List<FileDTO> fileDTOS = JSONUtil.toList(assessmentFiles, FileDTO.class);
                    settingDTO.setAssessmentFiles(fileDTOS);
                }

                // 受邀老师
                List<TeacherDTO> teachers = assessmentInviteIdMap.getOrDefault(item.getId(), Collections.emptyList()).stream()
                        .map(AssessmentInvitePO::getInviteeTeacherId)
                        .map(studentMap::get)
                        .map(student -> BeanUtil.copyProperties(student, TeacherDTO.class))
                        .toList();
                settingDTO.setInviteTeachers(teachers);
                List<AssessmentStudentRelationPO> relations = assessmentIdMap.getOrDefault(item.getId(), Collections.emptyList());

                // 构造参与学生 DTO 列表
                List<StudentDTO> studentDTOS = relations.stream()
                        .filter(relation -> relation.getRelationType().equals(0))
                        .map(AssessmentStudentRelationPO::getStudentId)
                        .map(studentMap::get)
                        .map(ite -> BeanUtil.copyProperties(ite, StudentDTO.class))
                        .toList();
                settingDTO.setStudents(studentDTOS);

                Map<String, List<StudentDTO>> schoolClassMap = relations.stream()
                        .filter(relation -> relation.getRelationType().equals(1))
                        .map(AssessmentStudentRelationPO::getStudentId)
                        .map(studentMap::get)
                        .filter(Objects::nonNull)
                        .map(ite -> BeanUtil.copyProperties(ite, StudentDTO.class))
                        .collect(Collectors.groupingBy(StudentDTO::getSchoolClass));
                List<SchoolClassStudentDTO> schoolClassStudents = schoolClassMap.entrySet().stream()
                        .map(student -> {
                            SchoolClassStudentDTO schoolClassStudentDTO = new SchoolClassStudentDTO();
                            schoolClassStudentDTO.setSchoolClass(student.getKey());
                            schoolClassStudentDTO.setStudents(student.getValue());
                            return schoolClassStudentDTO;
                        }).toList();
                settingDTO.setSchoolClassList(schoolClassStudents);

                settingDTO.setParticipatingStudents(relations.stream()
                        .map(relation -> {
                            AssessmentSettingsDTO.ParticipatingStudentDTO studentDTO = new AssessmentSettingsDTO.ParticipatingStudentDTO();
                            UserPO matchStudent = studentMap.getOrDefault(relation.getStudentId(), UserPO.EMPTY_USER);
                            studentDTO.setStudentName(matchStudent.getName());
                            studentDTO.setSchoolClass(matchStudent.getSchoolClass());
                            return studentDTO;
                        }).toList());

                List<AssessmentAppointmentSettingPO> matchAppoints = appointMap.getOrDefault(item.getId(), Collections.emptyList());
                settingDTO.setAppointmentSetting(AppointmentSettingDTO.builder()
                        .assessmentFailPunish(item.getAssessmentFailPunish())
                        .rescheduleAppointTime(item.getRescheduleAppointTime())
                        .timePeriods(matchAppoints.stream().map(appoint -> {
                            AppointmentSettingDTO.TimePeriod timePeriod = new AppointmentSettingDTO.TimePeriod();
                            timePeriod.setId(appoint.getId());
                            timePeriod.setLocation(appoint.getLocation());
                            timePeriod.setTimePeriod(appoint.getTimePeriod());
                            timePeriod.setParticipantLimit(appoint.getParticipantLimit());
                            return timePeriod;
                        }).toList())
                        .build());
                return settingDTO;
            });
        }
        // 如果没有数据，返回空分页对象
        return new Page<>(pagingQuery.getPage(), pagingQuery.getSize(), poPage.getTotal());
    }


    @Transactional(rollbackFor = Exception.class)
    public Boolean removeAssessmentSettings(AssessmentSettingsRemoveCmd deleteCmd) {
        assessmentStudentRelationService.remove(Wrappers.lambdaQuery(AssessmentStudentRelationPO.class)
                .eq(AssessmentStudentRelationPO::getAssessmentId, deleteCmd.getId()));
        recordService.remove(Wrappers.lambdaQuery(StudentAssessmentRecordPO.class)
                .eq(StudentAssessmentRecordPO::getAssessmentId, deleteCmd.getId()));
        questionAnswerService.remove(Wrappers.lambdaQuery(StudentAssessmentQuestionAnswerPO.class)
                .eq(StudentAssessmentQuestionAnswerPO::getAssessmentId, deleteCmd.getId()));
        questionBankService.deleteByAssessment(deleteCmd.getId());
        return assessmentSettingService.removeById(deleteCmd.getId());
    }

    public AssessmentSimpleDTO detail(Integer studentId, Integer assessmentId) {
        AssessmentSettingPO setting = assessmentSettingService.findById(assessmentId).orElseThrow(() -> new BusinessException(ErrorCodeEnums.ASSESSMENT_SETTING_NOT_FOUND));
        AssessmentSimpleDTO assessmentSimpleDTO = BeanUtil.copyProperties(setting, AssessmentSimpleDTO.class);
        Optional<StudentAssessmentRecordPO> recordOp = recordService.findByStudentIdAndAssessmentId(studentId, assessmentId);
        recordOp.ifPresent(item -> assessmentSimpleDTO.setFinishDefense(item.getEndDefenseTime() != null));
        return assessmentSimpleDTO;
    }

    /**
     * 设置考核预约
     * 该方法用于配置特定考核的预约设置，包括考核失败惩罚策略和具体的预约时间段
     *
     * @param appointmentCmd 预约设置创建命令对象，包含考核ID、失败惩罚策略、惩罚天数以及预约时间段列表
     * @return Boolean 操作结果，成功返回true
     */
    @Transactional(rollbackFor = Exception.class)
    public Boolean settingAppointment(AppointmentSettingCreateCmd appointmentCmd) {
        Integer assessmentId = appointmentCmd.getAssessmentId();
        AssessmentSettingPO setting = assessmentSettingService.findById(assessmentId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnums.ASSESSMENT_SETTING_NOT_FOUND));
        setting.setAssessmentFailPunish(appointmentCmd.getAssessmentFailPunish());
        setting.setRescheduleAppointTime(appointmentCmd.getRescheduleAppointTime());
        setting.setLastAppointTime(appointmentCmd.lastTimePeriod());
        assessmentSettingService.updateById(setting);

        // 获取需要添加和更新的预约设置列表
        List<AssessmentAppointmentSettingPO> appointmentSettingPOList = appointmentCmd.toAppointmentSettingPOList();
        List<AssessmentAppointmentSettingPO> needUpdate = appointmentSettingPOList.stream().filter(item -> item.getId() != null).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(needUpdate)) {
            // 绑定的预约设置
            List<AssessmentAppointmentSettingPO> existAppointments = appointmentSettingService.list(Wrappers.lambdaQuery(AssessmentAppointmentSettingPO.class)
                    .eq(AssessmentAppointmentSettingPO::getAssessmentId, assessmentId));
            // 完成的考核
            List<StudentAssessmentRecordPO> records = recordService.list(Wrappers.lambdaQuery(StudentAssessmentRecordPO.class)
                    .eq(StudentAssessmentRecordPO::getAssessmentId, assessmentId)
                    .in(StudentAssessmentRecordPO::getState, 1, 2));
            Map<String, StudentAssessmentRecordPO> studentAssessmentRecordMap = records.stream()
                    .collect(Collectors.toMap(StudentAssessmentRecordPO::assessmentIdAndStudentId, item -> item));
            Map<Integer, AssessmentAppointmentSettingPO> appointmentIdMap = existAppointments.stream().collect(Collectors.toMap(AssessmentAppointmentSettingPO::getId, item -> item));
            for (AssessmentAppointmentSettingPO assessmentAppointmentSettingPO : needUpdate) {
                AssessmentAppointmentSettingPO matchExistSetting = appointmentIdMap.get(assessmentAppointmentSettingPO.getId());
                if (matchExistSetting == null) {
                    throw new BusinessException(ErrorCodeEnums.ASSESSMENT_APPOINTMENT_SETTING_NOT_FOUND);
                }
                LocalDateTime oldTimePeriod = matchExistSetting.getTimePeriod();
                matchExistSetting.setTimePeriod(assessmentAppointmentSettingPO.getTimePeriod());
                matchExistSetting.setParticipantLimit(assessmentAppointmentSettingPO.getParticipantLimit());
                matchExistSetting.setLocation(assessmentAppointmentSettingPO.getLocation());
                appointmentSettingService.updateById(matchExistSetting);
                // 如果更新的时间段和数据库中的时间段不一致，则需要通知预约
                LocalDateTime newTimePeriod = assessmentAppointmentSettingPO.getTimePeriod();
                if (!Objects.equals(oldTimePeriod, newTimePeriod)) {
                    List<StudentAssessmentAppointmentPO> matchTimePeriodAppointments = studentAssessmentAppointmentService.list(Wrappers.lambdaQuery(StudentAssessmentAppointmentPO.class)
                            .eq(StudentAssessmentAppointmentPO::getAssessmentId, assessmentId)
                            .eq(StudentAssessmentAppointmentPO::getTimePeriod, oldTimePeriod));
                    if (CollectionUtils.isNotEmpty(matchTimePeriodAppointments)) {
                        // 如果该预约设置有对应的考核信息（相同的考核id+学生id），那就不需要通知
                        List<StudentAssessmentAppointmentPO> updateAppointments = matchTimePeriodAppointments
                                .stream()
                                .filter(period -> studentAssessmentRecordMap.get(period.assessmentIdAndStudentId()) == null)
                                .peek(item -> {
                                    item.setState(0);
                                    item.setTimePeriod(newTimePeriod);
                                })
                                .toList();
                        if (CollectionUtils.isNotEmpty(updateAppointments)) {
                            studentAssessmentAppointmentService.updateBatchById(updateAppointments);
                        }
                    }
                }
            }

            // 处理需要删除的预约设置
            Set<Integer> newAppointmentIds = needUpdate.stream().map(AssessmentAppointmentSettingPO::getId).collect(Collectors.toSet());
            List<AssessmentAppointmentSettingPO> needDeleteAppointments = existAppointments.stream().filter(item -> !newAppointmentIds.contains(item.getId())).toList();
            if (CollectionUtils.isNotEmpty(needDeleteAppointments)) {
                List<Integer> removeIds = needDeleteAppointments.stream().map(AssessmentAppointmentSettingPO::getId).toList();
                appointmentSettingService.removeBatchByIds(removeIds);
                // 删除关联的学生预约
                List<LocalDateTime> removeTimePeriods = needDeleteAppointments.stream().map(AssessmentAppointmentSettingPO::getTimePeriod).toList();
                studentAssessmentAppointmentService.remove(Wrappers.lambdaQuery(StudentAssessmentAppointmentPO.class)
                        .eq(StudentAssessmentAppointmentPO::getAssessmentId, appointmentCmd.getAssessmentId())
                        .in(StudentAssessmentAppointmentPO::getTimePeriod, removeTimePeriods));
            }
        }
        List<AssessmentAppointmentSettingPO> needAdd = appointmentSettingPOList.stream().filter(item -> item.getId() == null).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(needAdd)) {
            appointmentSettingService.saveBatch(needAdd);
        }
        return true;
    }

    public AssessmentAppointSettingSummaryDTO settingAppointmentSummary(Integer assessmentId) {
        List<AssessmentAppointmentSettingPO> appointmentSettingPOS = appointmentSettingService.list(Wrappers.lambdaQuery(AssessmentAppointmentSettingPO.class)
                .eq(AssessmentAppointmentSettingPO::getAssessmentId, assessmentId)
                .orderByAsc(AssessmentAppointmentSettingPO::getTimePeriod));

        List<StudentAssessmentAppointmentPO> studentAppointments = studentAssessmentAppointmentService.list(Wrappers.lambdaQuery(StudentAssessmentAppointmentPO.class)
                .eq(StudentAssessmentAppointmentPO::getAssessmentId, assessmentId)
                .orderByDesc(StudentAssessmentAppointmentPO::getAppointmentTime));

        // 总关联的学生
        List<AssessmentStudentRelationPO> relationStudents = assessmentStudentRelationService.list(Wrappers.lambdaQuery(AssessmentStudentRelationPO.class)
                .eq(AssessmentStudentRelationPO::getAssessmentId, assessmentId));
        List<Integer> allRelationStudentIds = relationStudents.stream().map(AssessmentStudentRelationPO::getStudentId).distinct().toList();
        Map<Integer, UserPO> userIdMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(allRelationStudentIds)) {
            // 查询学生信息
            List<UserPO> students = userService.listByIds(allRelationStudentIds);
            userIdMap.putAll(students.stream().collect(Collectors.toMap(UserPO::getId, item -> item)));
        }

        Map<LocalDateTime, List<StudentAssessmentAppointmentPO>> timePeriodMap = studentAppointments.stream().collect(Collectors.groupingBy(StudentAssessmentAppointmentPO::getTimePeriod));
        AssessmentAppointSettingSummaryDTO settingSummaryDTO = new AssessmentAppointSettingSummaryDTO();
        settingSummaryDTO.setAlreadyAppointCount(studentAppointments.size());

        // 剔除预约的学生，就是未预约的学生
        Set<Integer> studentIds = studentAppointments.stream().map(StudentAssessmentAppointmentPO::getStudentId).collect(Collectors.toSet());
        relationStudents.removeIf(item -> studentIds.contains(item.getStudentId()));
        settingSummaryDTO.setNotAppointCount(relationStudents.size());
        settingSummaryDTO.setNotAppointStudents(relationStudents.stream()
                .map(item -> {
                    UserPO user = userIdMap.getOrDefault(item.getStudentId(), UserPO.EMPTY_USER);
                    return AssessmentAppointSettingSummaryDTO.AppointmentStudentDTO.builder()
                            .id(user.getId())
                            .schoolClass(user.getSchoolClass())
                            .studentName(user.getName())
                            .build();
                })
                .toList());
        settingSummaryDTO.setTimePeriods(appointmentSettingPOS.stream()
                .map(item -> {
                    List<StudentAssessmentAppointmentPO> matchAppointments = timePeriodMap.getOrDefault(item.getTimePeriod(), Collections.emptyList());
                    return AssessmentAppointSettingDTO.builder()
                            .timePeriod(item.getTimePeriod())
                            .location(item.getLocation())
                            .appointmentStudents(matchAppointments.stream()
                                    .map(appointment -> toAppointmentStudentDTO(appointment, userIdMap.getOrDefault(appointment.getStudentId(), UserPO.EMPTY_USER)))
                                    .toList())
                            .alreadyAssessStudents(matchAppointments.stream()
                                    .filter(record -> Objects.equals(record.getState(), 2))
                                    .map(appointment -> toAppointmentStudentDTO(appointment, userIdMap.getOrDefault(appointment.getStudentId(), UserPO.EMPTY_USER)))
                                    .toList())
                            .expiredStudents(matchAppointments.stream()
                                    .filter(record -> Objects.equals(record.getState(), 3) || Objects.equals(record.getState(), 4))
                                    .map(appointment -> toAppointmentStudentDTO(appointment, userIdMap.getOrDefault(appointment.getStudentId(), UserPO.EMPTY_USER)))
                                    .toList())
                            .build();
                }).toList());
        return settingSummaryDTO;
    }

    private AssessmentAppointSettingDTO.AppointmentStudentDTO toAppointmentStudentDTO(StudentAssessmentAppointmentPO appointmentPO, UserPO userPO) {
        AssessmentAppointSettingDTO.AppointmentStudentDTO studentDTO = new AssessmentAppointSettingDTO.AppointmentStudentDTO();
        studentDTO.setId(appointmentPO.getId());
        studentDTO.setStudentName(userPO.getName());
        studentDTO.setSchoolClass(userPO.getSchoolClass());
        Integer punishState = appointmentPO.getPunishState();
        if (punishState != null && !Objects.equals(punishState, -1)) {
            studentDTO.setPunishState(punishState);
        }
        return studentDTO;
    }

    public Boolean liftPunish(LiftPunishCmd liftPunishCmd) {
        return studentAssessmentAppointmentService.update(Wrappers.lambdaUpdate(StudentAssessmentAppointmentPO.class)
                .set(StudentAssessmentAppointmentPO::getState, 4)
                .set(StudentAssessmentAppointmentPO::getPunishState, 1)
                .eq(StudentAssessmentAppointmentPO::getId, liftPunishCmd.getId()));
    }

    public Boolean cancelStudentAppointment(CancelStudentAppointmentCmd cancelStudentAppointmentCmd) {
        return studentAssessmentAppointmentService.removeById(cancelStudentAppointmentCmd.getAppointmentId());
    }
}
