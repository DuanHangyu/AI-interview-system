package system.assessment.defense.application.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import system.assessment.defense.application.dto.*;
import system.assessment.defense.application.manage.OpenAiManager;
import system.assessment.defense.domain.entity.TeacherSummary;
import system.assessment.defense.domain.event.StudentCreateEvent;
import system.assessment.defense.infrastructure.common.FileByteDTO;
import system.assessment.defense.infrastructure.common.HttpUtils;
import system.assessment.defense.infrastructure.emuns.ErrorCodeEnums;
import system.assessment.defense.infrastructure.exception.BusinessException;
import system.assessment.defense.infrastructure.repository.dao.po.*;
import system.assessment.defense.infrastructure.repository.dao.service.*;

import java.time.LocalDateTime;
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

    private final AssessmentSettingService settingService;

    private final StudentAssessmentAppointmentService appointmentService;

    private final AssessmentStudentRelationService relationService;

    private final StudentAssessmentRecordService recordService;


    private final OpenAiManager openAiManager;

    private final HttpUtils httpUtils;

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

    @NotNull
    private static List<AssessmentStudentRelationPO> getAssessmentStudentRelationPOS(List<UserPO> studentPos, List<AssessmentSettingPO> settings) {
        List<AssessmentStudentRelationPO> relations = new ArrayList<>(studentPos.size() * settings.size());
        for (AssessmentSettingPO setting : settings) {
            for (UserPO studentPo : studentPos) {
                AssessmentStudentRelationPO relationPO = new AssessmentStudentRelationPO();
                relationPO.setAssessmentId(setting.getId());
                relationPO.setStudentId(studentPo.getId());
                relations.add(relationPO);
            }
        }
        return relations;
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

//    @Scheduled(cron = "0 0 * * * ?")
    public void batchAnalysisUserInfo() {
        List<UserPO> allStudents = userService.list(Wrappers.lambdaQuery(UserPO.class)
                .eq(UserPO::getType, 0));
        List<Integer> studentIds = allStudents.stream().map(UserPO::getId).toList();
        List<StudentAssessmentRecordPO> records = recordService.findbyStudentIds(studentIds);
        Map<Integer, Optional<StudentAssessmentRecordPO>> studentRecordMap = records.stream().collect(Collectors.groupingBy(StudentAssessmentRecordPO::getStudentId, Collectors.maxBy((o1, o2) -> o2.getCreateTime().compareTo(o1.getCreateTime()))));

        List<Integer> analysisStudentIds = new ArrayList<>();
        for (UserPO student : allStudents) {
            Integer studentId = student.getId();
            Optional<StudentAssessmentRecordPO> recordOp = studentRecordMap.get(studentId);
            if (recordOp == null) {
                continue;
            }
            LocalDateTime updateTime = student.getUpdateTime();
            if (recordOp.isPresent()) {
                StudentAssessmentRecordPO record = recordOp.get();
                if (record.getCreateTime().isAfter(updateTime)) {
                    analysisStudentIds.add(student.getId());
                }
            }
        }
        if (CollectionUtils.isEmpty(analysisStudentIds)) {
            return;
        }
        batchAnalysisUserInfo(analysisStudentIds);
    }

    private void batchAnalysisUserInfo(List<Integer> studentIds) {
        List<StudentAssessmentRecordPO> studentAssessments = recordService.findbyStudentIds(studentIds);
        Map<Integer, List<StudentAssessmentRecordPO>> assessmentStudentIdMap = studentAssessments.stream().collect(Collectors.groupingBy(StudentAssessmentRecordPO::getStudentId));
        for (Integer studentId : studentIds) {
            analysisUserInfo(studentId, assessmentStudentIdMap.get(studentId));
        }
    }

    private static final String STUDENT_ANALYSIS_SCHEMA = """
            {
              "type": "object",
              "required": ["abilityAdvantageAnalysis", "developmentPotentialAssessment"],
              "additionalProperties": false,
              "properties": {
                "abilityAdvantageAnalysis": {
                  "type": "string",
                  "description": "能力优势分析"
                },
                "developmentPotentialAssessment": {
                  "type": "string",
                  "description": "发展潜力评估"
                }
              }
            }
            """;

    private void analysisUserInfo(Integer studentId, List<StudentAssessmentRecordPO> recordPOS) {
        try {
            StringBuilder promptBuilder = new StringBuilder("请根据学生的答辩结果（如果答辩结果为空，则不参与评估），对学生进行评估，生成能力优势分析和发展潜力评估").append("\n");
            promptBuilder.append("【答辩结果】：").append("\n");
            if (CollectionUtils.isNotEmpty(recordPOS)) {
                int index = 1;
                for (StudentAssessmentRecordPO recordPO : recordPOS) {
                    promptBuilder.append(index++).append(":").append(recordPO.getDefenseResult()).append("\n");
                }
            }

            String text;
            text = openAiManager.chatCompletionWithSchema(
                    openAiManager.getTextModel(),
                    "你是一位大学评估专家，请使用简体中文进行回答",
                    promptBuilder.toString(),
                    "student_analysis",
                    STUDENT_ANALYSIS_SCHEMA
            );

            StudentAnalysis analysis = JSONUtil.toBean(text, StudentAnalysis.class);
            userService.update(Wrappers.lambdaUpdate(UserPO.class)
                    .set(UserPO::getAbilityAdvantageAnalysis, analysis.getAbilityAdvantageAnalysis())
                    .set(UserPO::getDevelopmentPotentialAssessment, analysis.getDevelopmentPotentialAssessment())
                    .eq(UserPO::getId, studentId));
        } catch (Exception e) {
            log.error("分析学生信息异常", e);
        }
    }

    private String getMimeType(String fileName) {
        String extendName = fileName.substring(fileName.lastIndexOf(".") + 1);
        return switch (extendName) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "pdf" -> "application/pdf";
            case "doc", "docx" -> "application/msword";
            case "xls", "xlsx" -> "application/vnd.ms-excel";
            case "tex" -> "application/x-tex";
            default -> "application/octet-stream";
        };
    }

    public List<StudentDTO> allStudent() {
        return userService.list(Wrappers.lambdaQuery(UserPO.class)
                .eq(UserPO::getType, 0))
                .stream()
                .map(studentPO -> BeanUtil.copyProperties(studentPO, StudentDTO.class))
                .toList();
    }

    @Data
    private static class StudentAnalysis {

        private String abilityAdvantageAnalysis;

        private String developmentPotentialAssessment;
    }
}
