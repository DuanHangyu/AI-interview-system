package system.assessment.defense.application.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import system.assessment.defense.application.dto.*;
import system.assessment.defense.application.dto.backed.RecordRetakeCmd;
import system.assessment.defense.application.dto.backed.StudentRecordChangeScoreCmd;
import system.assessment.defense.application.dto.backed.StudentRecordExcelDTO;
import system.assessment.defense.application.dto.backed.StudentRecordExcelQuery;
import system.assessment.defense.domain.entity.TeacherSummary;
import system.assessment.defense.infrastructure.common.ExcelUtils;
import system.assessment.defense.infrastructure.emuns.ErrorCodeEnums;
import system.assessment.defense.infrastructure.exception.BusinessException;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentSettingPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentQuestionAnswerPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentRecordPO;
import system.assessment.defense.infrastructure.repository.dao.po.UserPO;
import system.assessment.defense.infrastructure.repository.dao.service.AssessmentSettingService;
import system.assessment.defense.infrastructure.repository.dao.service.StudentAssessmentQuestionAnswerService;
import system.assessment.defense.infrastructure.repository.dao.service.StudentAssessmentRecordService;
import system.assessment.defense.infrastructure.repository.dao.service.UserService;

import system.assessment.defense.application.dto.backed.RecordReviewCmd;
import system.assessment.defense.application.service.interview.AnswerRows;
import system.assessment.defense.infrastructure.common.CriteriaParser;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @USER taoHouChao
 * @DATE 14:09 2025/8/17
 */
@Service
@RequiredArgsConstructor
public class StudyRecordService {

    private final StudentAssessmentRecordService studentAssessmentService;

    private final StudentAssessmentQuestionAnswerService questionAnswerService;

    private final UserService userService;

    private final AssessmentSettingService assessmentSettingService;

    /**
     * 分页查询学习记录列表
     * <p>
     * 根据学生姓名和考核主题进行模糊查询，支持分页。如果提供了学生姓名或考核主题，
     * 将先查询匹配的学生ID列表或考核设置ID列表，再根据这些ID查询学习记录。
     * 最终将查询结果转换为 {@link StudyRecordDTO} 并返回分页结果。
     *
     * @param pagingQuery 分页查询参数对象，包含页码、页面大小、学生姓名和考核主题等查询条件
     * @return 返回分页的学习记录数据传输对象（{@link StudyRecordDTO}）列表
     */
    @Transactional(readOnly = true)
    public IPage<StudyRecordDTO> recordList(StudyRecordPagingQuery pagingQuery) {
        TeacherSummary teacherSummary = userService.findTeacherSummaryById(StpUtil.getLoginIdAsInt()).orElseThrow(() -> new RuntimeException("用户不存在"));
        List<Integer> studentIds = teacherSummary.getStudentIds();
        if (CollectionUtils.isEmpty(studentIds)) {
            return new Page<>(pagingQuery.getPage(), pagingQuery.getSize());
        }
        String studentName = pagingQuery.getStudentName();
        String theme = pagingQuery.getTheme();
        List<Integer> searchStudentIds = null;
        List<Integer> assessmentIds = null;

        // 如果提供了学生姓名，则根据姓名模糊查询用户ID列表
        if (StringUtils.isNotBlank(studentName)) {
            List<UserPO> users = userService.list(Wrappers.lambdaQuery(UserPO.class)
                    .like(UserPO::getName, studentName));
            if (CollectionUtils.isEmpty(users)) {
                // 如果没有匹配的用户，返回空分页结果
                return new Page<>(pagingQuery.getPage(), pagingQuery.getSize());
            }
            searchStudentIds = users.stream().map(UserPO::getId).toList();
        }

        // 如果提供了考核主题，则根据主题模糊查询考核设置ID列表
        if (StringUtils.isNotBlank(theme)) {
            List<AssessmentSettingPO> assessments = assessmentSettingService.list(Wrappers.lambdaQuery(AssessmentSettingPO.class)
                    .like(AssessmentSettingPO::getTheme, theme));
            if (CollectionUtils.isEmpty(assessments)) {
                // 如果没有匹配的考核设置，返回空分页结果
                return new Page<>(pagingQuery.getPage(), pagingQuery.getSize());
            }
            assessmentIds = assessments.stream().map(AssessmentSettingPO::getId).toList();
        }

        // 根据学生ID和考核设置ID分页查询学习记录
        IPage<StudentAssessmentRecordPO> poPage = studentAssessmentService.page(new Page<>(pagingQuery.getPage(), pagingQuery.getSize()),
                Wrappers.lambdaQuery(StudentAssessmentRecordPO.class)
                        .in(StudentAssessmentRecordPO::getStudentId, studentIds)
                        // 已完成和评分中的记录；评分中的记录教师可看到进度、可重新评分
                        .in(StudentAssessmentRecordPO::getState, 1, 2)
                        .in(CollectionUtils.isNotEmpty(searchStudentIds), StudentAssessmentRecordPO::getStudentId, searchStudentIds)
                        .in(CollectionUtils.isNotEmpty(assessmentIds), StudentAssessmentRecordPO::getAssessmentId, assessmentIds)
                        .orderByDesc(StudentAssessmentRecordPO::getUpdateTime));

        List<StudentAssessmentRecordPO> records = poPage.getRecords();

        // 如果查询到记录，则填充DTO对象并返回
        if (CollectionUtils.isNotEmpty(records)) {
            // 提取所有涉及的考核ID和学生ID，用于批量查询详细信息
            List<Integer> assessmentIdList = records.stream().map(StudentAssessmentRecordPO::getAssessmentId).distinct().toList();
            List<Integer> studentIdList = records.stream().map(StudentAssessmentRecordPO::getStudentId).distinct().toList();

            // 批量查询考核设置和用户信息，并构建映射表
            Map<Integer, AssessmentSettingPO> assessmentIdMap = assessmentSettingService.listByIds(assessmentIdList).stream()
                    .collect(Collectors.toMap(AssessmentSettingPO::getId, item -> item));
            Map<Integer, UserPO> userIdMap = userService.listByIds(studentIdList).stream()
                    .collect(Collectors.toMap(UserPO::getId, item -> item));

            // 将PO对象转换为DTO对象
            return poPage.convert(item -> {
                AssessmentSettingPO setting = assessmentIdMap.get(item.getAssessmentId());
                UserPO user = userIdMap.get(item.getStudentId());
                return StudyRecordDTO.builder()
                        .id(item.getId())
                        .studentName(user.getName())
                        .theme(setting.getTheme())
                        .score(item.getScore())
                        .checkScore(item.getCheckScore())
                        .totalScore(setting.getTotalScore())
                        .assessmentTime(Optional.ofNullable(item.getStartTime()).orElse(item.getCreateTime()))
                        .state(item.getState())
                        .finalScore(item.frontScore())
                        .reviewed(StringUtils.isNotBlank(item.getTeacherReview()))
                        .passScore(setting.getPassScore())
                        .studentAccount(user.getAccount())
                        .schoolClass(user.getSchoolClass())
                        .build();
            });
        }

        // 如果没有记录，返回空分页结果
        return new Page<>(pagingQuery.getPage(), pagingQuery.getSize());
    }

    /**
     * 校验当前教师能否操作该记录：管理员可操作全部，教师只能操作自己面试中的学生。
     */
    public StudentAssessmentRecordPO checkAccessible(Integer recordId) {
        StudentAssessmentRecordPO record = Optional.ofNullable(recordId == null ? null : studentAssessmentService.getById(recordId))
                .orElseThrow(() -> new BusinessException(ErrorCodeEnums.RECORD_NOT_FOUND));
        TeacherSummary teacherSummary = userService.findTeacherSummaryById(StpUtil.getLoginIdAsInt())
                .orElseThrow(() -> new BusinessException(ErrorCodeEnums.USER_NOT_FOUND));
        List<Integer> studentIds = teacherSummary.getStudentIds();
        if (CollectionUtils.isEmpty(studentIds) || !studentIds.contains(record.getStudentId())) {
            throw new BusinessException(403, "无权操作该记录");
        }
        return record;
    }

    /**
     * 教师复核：按维度调整分数并填写评语。最终得分 = 调整后各维度之和，AI 原始评分保留不变。
     */
    public Boolean review(RecordReviewCmd cmd) {
        int teacherId = StpUtil.getLoginIdAsInt();
        StudentAssessmentRecordPO record = checkAccessible(cmd.getRecordId());
        if (!Objects.equals(record.getState(), 1) || StringUtils.isBlank(record.getDefenseResult())) {
            throw new BusinessException(400, "评分完成后才能复核");
        }
        AssessmentSettingPO setting = assessmentSettingService.getById(record.getAssessmentId());
        AssessmentEvaluationDTO evaluation = JSONUtil.toBean(record.getDefenseResult(), AssessmentEvaluationDTO.class);
        List<AnalysisDTO> aiDimensions = Optional.ofNullable(evaluation.getAnalysis())
                .map(AnalysisBO::getAnalysis).orElse(List.of());
        Map<String, Integer> maxScores = CriteriaParser.maxScores(setting == null ? null : setting.getAssessmentCriteria());
        Map<String, Integer> submitted = Optional.ofNullable(cmd.getDimensions()).orElse(List.of()).stream()
                .filter(d -> d.getName() != null)
                .collect(Collectors.toMap(TeacherReviewBO.DimensionScore::getName,
                        d -> Optional.ofNullable(d.getScore()).orElse(0), (a, b) -> b));

        List<TeacherReviewBO.DimensionScore> dimensions = new java.util.ArrayList<>();
        int total = 0;
        for (AnalysisDTO dimension : aiDimensions) {
            int score = submitted.getOrDefault(dimension.getName(),
                    Optional.ofNullable(dimension.getScore()).orElse(0));
            Integer max = Optional.ofNullable(dimension.getMaxScore())
                    .orElseGet(() -> CriteriaParser.maxScoreOf(maxScores, dimension.getName()));
            if (score < 0 || (max != null && score > max)) {
                throw new BusinessException(400, String.format("“%s”的分数应在 0～%s 之间",
                        dimension.getName(), max == null ? "满分" : max));
            }
            dimensions.add(new TeacherReviewBO.DimensionScore(dimension.getName(), score));
            total += score;
        }
        Integer totalLimit = aiDimensions.stream().allMatch(d -> d.getMaxScore() != null)
                ? Integer.valueOf(aiDimensions.stream().mapToInt(AnalysisDTO::getMaxScore).sum())
                : (setting == null ? null : setting.getTotalScore());
        if (totalLimit != null && total > totalLimit) {
            throw new BusinessException(400, "各维度分数之和不能超过总分 " + totalLimit);
        }
        String reviewerName = userService.findById(teacherId).map(UserPO::getName).orElse(null);
        TeacherReviewBO review = TeacherReviewBO.builder()
                .dimensions(dimensions)
                .comment(StringUtils.trimToNull(cmd.getComment()))
                .reviewerId(teacherId)
                .reviewerName(reviewerName)
                .reviewTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();
        return studentAssessmentService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                .set(StudentAssessmentRecordPO::getCheckScore, total)
                .set(StudentAssessmentRecordPO::getTeacherReview, JSONUtil.toJsonStr(review))
                .eq(StudentAssessmentRecordPO::getId, record.getId())
                .eq(StudentAssessmentRecordPO::getState, 1));
    }

    public Boolean changeScore(StudentRecordChangeScoreCmd changeScoreCmd) {
        return studentAssessmentService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                .set(StudentAssessmentRecordPO::getCheckScore, changeScoreCmd.getCheckScore())
                .eq(StudentAssessmentRecordPO::getId, changeScoreCmd.getId()));
    }

    public void exportRecordExcel(StudentRecordExcelQuery excelQuery) {
        List<Integer> recordIds = excelQuery.getRecordIds();
        String theme = excelQuery.getTheme();
        if (StringUtils.isNotBlank(theme)) {
            List<AssessmentSettingPO> settings = assessmentSettingService.list(Wrappers.lambdaQuery(AssessmentSettingPO.class)
                    .like(AssessmentSettingPO::getTheme, theme));
            if (CollectionUtils.isEmpty(settings)) {
                return;
            }
            List<Integer> assessmentIds = settings.stream()
                    .map(AssessmentSettingPO::getId)
                    .toList();
            List<StudentAssessmentRecordPO> records = studentAssessmentService.list(Wrappers.lambdaQuery(StudentAssessmentRecordPO.class)
                    .in(StudentAssessmentRecordPO::getAssessmentId, assessmentIds)
                    .orderByDesc(StudentAssessmentRecordPO::getId));
            exportRecords(records, settings);
        }else {
            List<StudentAssessmentRecordPO> records = studentAssessmentService.list(Wrappers.lambdaQuery(StudentAssessmentRecordPO.class)
                    .in(StudentAssessmentRecordPO::getId, recordIds)
                    .orderByDesc(StudentAssessmentRecordPO::getId));
            List<Integer> assessmentIds = records.stream()
                    .map(StudentAssessmentRecordPO::getAssessmentId)
                    .distinct()
                    .toList();
            List<AssessmentSettingPO> settings = assessmentSettingService.listByIds(assessmentIds);
            exportRecords(records, settings);
        }
    }

    private void exportRecords(List<StudentAssessmentRecordPO> records, List<AssessmentSettingPO> settings) {
        List<Integer> studentIds = records.stream()
                .map(StudentAssessmentRecordPO::getStudentId)
                .distinct()
                .toList();
        List<Integer> recordAssessmentIds = records.stream()
                .map(StudentAssessmentRecordPO::getAssessmentId)
                .distinct()
                .toList();
        List<StudentAssessmentQuestionAnswerPO> questionAnswers = questionAnswerService.list(Wrappers.lambdaQuery(StudentAssessmentQuestionAnswerPO.class)
                .in(StudentAssessmentQuestionAnswerPO::getAssessmentId, recordAssessmentIds)
                .in(StudentAssessmentQuestionAnswerPO::getStudentId, studentIds));
        Map<Integer, Map<Integer, List<StudentAssessmentQuestionAnswerPO>>> assessmentStudentIdMap = questionAnswers.stream()
                .collect(Collectors.groupingBy(StudentAssessmentQuestionAnswerPO::getAssessmentId, Collectors.groupingBy(StudentAssessmentQuestionAnswerPO::getStudentId)));
        // 批量查询用户信息
        Map<Integer, UserPO> studentIdMap = userService.listByIds(studentIds)
                .stream()
                .collect(Collectors.toMap(UserPO::getId, item -> item));

        Map<Integer, AssessmentSettingPO> assessmentIdMap = settings.stream()
                .collect(Collectors.toMap(AssessmentSettingPO::getId, item -> item));

        List<StudentRecordExcelDTO> recordExcels = records.stream()
                .map(record -> {
                    AssessmentSettingPO assessmentSettingPO = assessmentIdMap.getOrDefault(record.getAssessmentId(), new AssessmentSettingPO());
                    UserPO user = studentIdMap.getOrDefault(record.getStudentId(), UserPO.EMPTY_USER);
                    StudentRecordExcelDTO excelDTO = new StudentRecordExcelDTO();
                    excelDTO.setStudentName(user.getName());
                    excelDTO.setIdNumber(user.getAccount());
                    excelDTO.setTheme(assessmentSettingPO.getTheme());
                    excelDTO.setTotalScore(assessmentSettingPO.getTotalScore());
                    excelDTO.setPassScore(assessmentSettingPO.getPassScore());
                    excelDTO.setScore(record.getScore());
                    excelDTO.setCheckScore(record.getCheckScore());
                    excelDTO.setDefenseAnswer(record.getDefenseContent());
                    List<StudentAssessmentQuestionAnswerPO> matchQuestionAnswers = assessmentStudentIdMap.getOrDefault(record.getAssessmentId(), new HashMap<>()).getOrDefault(record.getStudentId(), Collections.emptyList());
                    Map<Integer, List<StudentAssessmentQuestionAnswerPO>> followRows = AnswerRows.followUps(matchQuestionAnswers);
                    StringBuilder questionAnswerBuilder = new StringBuilder();
                    for (StudentAssessmentQuestionAnswerPO questionAnswer : AnswerRows.mains(matchQuestionAnswers)) {
                        questionAnswerBuilder
                                .append("问题：").append(questionAnswer.getQuestion())
                                .append("\n")
                                .append("回答：").append(questionAnswer.getAnswer())
                                .append("\n");
                        for (StudentAssessmentQuestionAnswerPO follow : followRows.getOrDefault(questionAnswer.getId(), List.of())) {
                            questionAnswerBuilder
                                    .append("追问：").append(follow.getQuestion())
                                    .append("\n")
                                    .append("追问回答：").append(follow.getAnswer())
                                    .append("\n");
                        }
                        questionAnswerBuilder.append("\n");
                    }
                    excelDTO.setQuestionAnswer(questionAnswerBuilder.toString());
                    String defenseResult = record.getDefenseResult();
                    AssessmentEvaluationDTO evaluationDTO = JSONUtil.toBean(defenseResult, AssessmentEvaluationDTO.class);
                    AnalysisBO analysis = evaluationDTO.getAnalysis();
                    List<AnalysisDTO> analysisList = analysis.getAnalysis();
                    StringBuilder analysisBuilder = new StringBuilder();
                    for (AnalysisDTO analysisDTO : analysisList) {
                        analysisBuilder
                                .append("考核指标：").append(analysisDTO.getName())
                                .append("\n")
                                .append("得分：").append(analysisDTO.getScore())
                                .append("\n")
                                .append("评价：").append(analysisDTO.getDescription())
                                .append("\n")
                                .append("\n");
                    }
                    excelDTO.setAnalysis(analysisBuilder.toString());
                    excelDTO.setSuggestions(String.join("\n", analysis.getSuggestions()));
                    excelDTO.setStrengths(String.join("\n", analysis.getStrengths()));
                    excelDTO.setWeaknesses(String.join("\n", analysis.getWeaknesses()));
                    excelDTO.setAssessTime(record.getUpdateTime());
                    return excelDTO;
                }).toList();
        ExcelUtils.exportTemplate(recordExcels, "学生记录");
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean retake(RecordRetakeCmd retakeCmd) {
        Integer recordId = retakeCmd.getRecordId();
        StudentAssessmentRecordPO record = studentAssessmentService.getById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnums.RECORD_NOT_FOUND);
        }
        return studentAssessmentService.update(Wrappers.lambdaUpdate(StudentAssessmentRecordPO.class)
                .set(StudentAssessmentRecordPO::getState, 3)
                .eq(StudentAssessmentRecordPO::getId, recordId));
    }
}
