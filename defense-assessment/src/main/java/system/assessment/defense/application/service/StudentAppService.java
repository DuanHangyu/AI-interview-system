package system.assessment.defense.application.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import system.assessment.defense.application.dto.*;
import system.assessment.defense.domain.entity.TeacherSummary;
import system.assessment.defense.domain.event.StudentCreateEvent;
import system.assessment.defense.infrastructure.emuns.ErrorCodeEnums;
import system.assessment.defense.infrastructure.emuns.UserRoleEnums;
import system.assessment.defense.infrastructure.exception.BusinessException;
import system.assessment.defense.infrastructure.repository.dao.po.*;
import system.assessment.defense.infrastructure.repository.dao.service.*;

import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * @USER taoHouChao
 * @DATE 21:09 2025/8/7
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentAppService {

    private final UserService userService;

    private final StudentAssessmentAppointmentService appointmentService;

    private final AssessmentStudentRelationService relationService;

    private final StudentAssessmentRecordService recordService;

    private final StudentAssessmentQuestionAnswerService questionAnswerService;

    private final ThreadPoolExecutor threadPoolExecutor;

    private final ApplicationEventPublisher publisher;

    public IPage<StudentDTO> pageList(StudentPageQuery query) {
        int teacherId = StpUtil.getLoginIdAsInt();
        TeacherSummary teacherSummary = userService.findTeacherSummaryById(teacherId).orElseThrow(() -> new BusinessException(ErrorCodeEnums.USER_NOT_FOUND));
        List<Integer> studentIds = teacherSummary.getStudentIds();
        if (CollectionUtils.isEmpty(studentIds)) {
            return new Page<>();
        }
        IPage<UserPO> page = userService.page(new Page<>(query.getPage(), query.getSize()),
                Wrappers.lambdaQuery(UserPO.class)
                        .in(UserPO::getId, studentIds)
                        .like(StringUtils.isNotBlank(query.getName()), UserPO::getName, query.getName())
                        .like(StringUtils.isNotBlank(query.getPhone()), UserPO::getPhone, query.getPhone())
                        .like(StringUtils.isNotBlank(query.getAccount()), UserPO::getAccount, query.getAccount())
                        .like(StringUtils.isNotBlank(query.getSchoolClass()), UserPO::getSchoolClass, query.getSchoolClass()));
        return page.convert(studentPO -> BeanUtil.copyProperties(studentPO, StudentDTO.class));
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean createStudent(StudentCreateCmd createCmd) {
        UserPO userPO = createCmd.toPO();
        boolean save = userService.save(userPO);
        saveRelation(userPO);
        publisher.publishEvent(new StudentCreateEvent(this, userPO));
        return save;
    }

    private void saveRelation(UserPO userPO){
        String schoolClass = userPO.getSchoolClass();
        List<Integer> studentIds = userService.list(Wrappers.lambdaQuery(UserPO.class)
                        .eq(UserPO::getType, 0)
                        .eq(UserPO::getSchoolClass, schoolClass)).stream()
                .map(UserPO::getId)
                .toList();
        List<Integer> assessmentIds = relationService.list(Wrappers.lambdaQuery(AssessmentStudentRelationPO.class)
                        .eq(AssessmentStudentRelationPO::getRelationType, 1)
                        .in(AssessmentStudentRelationPO::getStudentId, studentIds)).stream()
                .map(AssessmentStudentRelationPO::getAssessmentId)
                .distinct()
                .toList();
        List<AssessmentStudentRelationPO> relations = assessmentIds.stream()
                .map(settingId -> AssessmentStudentRelationPO.builder()
                        .relationType(1)
                        .assessmentId(settingId)
                        .studentId(userPO.getId())
                        .build())
                .toList();
        relationService.saveBatch(relations);
    }

    public Boolean modifyStudent(StudentModifyCmd modifyCmd) {
        return userService.updateById(modifyCmd.toPO());
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteStudent(StudentRemoveCmd removeCmd) {
        relationService.remove(Wrappers.lambdaQuery(AssessmentStudentRelationPO.class)
                .eq(AssessmentStudentRelationPO::getStudentId, removeCmd.getId()));
        recordService.remove(Wrappers.lambdaQuery(StudentAssessmentRecordPO.class)
                .eq(StudentAssessmentRecordPO::getStudentId, removeCmd.getId()));
        questionAnswerService.remove(Wrappers.lambdaQuery(StudentAssessmentQuestionAnswerPO.class)
                .eq(StudentAssessmentQuestionAnswerPO::getStudentId, removeCmd.getId()));
        appointmentService.remove(Wrappers.lambdaQuery(StudentAssessmentAppointmentPO.class)
                .eq(StudentAssessmentAppointmentPO::getStudentId, removeCmd.getId()));
        return userService.removeById(removeCmd.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean importStudent(List<StudentExcelDTO> students) {
        checkImportStudent(students);
        List<String> userAccounts = students.stream().map(StudentExcelDTO::getAccount).distinct().toList();
        Set<String> existUsers = userService.list(Wrappers.lambdaQuery(UserPO.class)
                        .in(UserPO::getAccount, userAccounts)).stream()
                .map(UserPO::getAccount)
                .collect(Collectors.toSet());
        if (CollectionUtils.isNotEmpty(existUsers)) {
            throw new BusinessException(ErrorCodeEnums.EXIST_STUDENT);
        }
        List<UserPO> studentPos = students.stream()
                .map(item -> UserPO.builder()
                        .name(item.getName())
                        .account(item.getAccount())
                        .password(item.getPassword())
                        .schoolClass(item.getSchoolClass())
                        .type(UserRoleEnums.STUDENT.getRole())
                        .build())
                .toList();
        userService.saveBatch(studentPos);
        threadPoolExecutor.execute(() -> {
            for (UserPO studentPo : studentPos) {
                saveRelation(studentPo);
            }
        });
        return true;
    }

    private void checkImportStudent(List<StudentExcelDTO> students) {
        for (StudentExcelDTO student : students) {
            if (StrUtil.isBlank(student.getName())) {
                throw new BusinessException(ErrorCodeEnums.IMPORT_STUDENT_NAME_EMPTY);
            }
            if (StrUtil.isBlank(student.getAccount())) {
                throw new BusinessException(ErrorCodeEnums.IMPORT_STUDENT_NAME_EMPTY);
            }
            if (StrUtil.isBlank(student.getPassword())) {
                throw new BusinessException(ErrorCodeEnums.IMPORT_STUDENT_PASSWORD_EMPTY);
            }
        }
    }

    public List<StudentExcelDTO> exportStudent(StudentExportCmd exportCmd) {
        if (Objects.equals(Boolean.TRUE, exportCmd.getAll())) {
            return userService.list(Wrappers.lambdaQuery(UserPO.class)
                            .eq(UserPO::getType, 0)).stream()
                    .map(item -> StudentExcelDTO.builder()
                            .name(item.getName())
                            .account(item.getAccount())
                            .password(item.getPassword())
                            .schoolClass(item.getSchoolClass())
                            .build())
                    .toList();
        }
        return userService.listByIds(exportCmd.getIds()).stream()
                .map(item -> StudentExcelDTO.builder()
                        .name(item.getName())
                        .account(item.getAccount())
                        .password(item.getPassword())
                        .schoolClass(item.getSchoolClass())
                        .build())
                .toList();
    }

    public List<StudentDTO> allStudent() {
        return userService.list(Wrappers.lambdaQuery(UserPO.class)
                .eq(UserPO::getType, 0))
                .stream()
                .map(studentPO -> BeanUtil.copyProperties(studentPO, StudentDTO.class))
                .toList();
    }
}
