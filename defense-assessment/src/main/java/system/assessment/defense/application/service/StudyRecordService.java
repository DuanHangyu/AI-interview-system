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
import system.assessment.defense.application.service.exam.ExamSessionService;
import system.assessment.defense.application.service.exam.ScoreCalculator;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentAppointmentSettingPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentAppointmentPO;
import system.assessment.defense.infrastructure.repository.dao.service.AssessmentAppointmentSettingService;
import system.assessment.defense.infrastructure.repository.dao.service.StudentAssessmentAppointmentService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
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

    private final StudentAssessmentAppointmentService appointmentService;

    private final AssessmentAppointmentSettingService appointmentSettingService;

    /**
     * 面试记录列表：只包含当前教师名下学生的已完成 / 评分中记录。
     * 得分是从评分结果中计算出来的，所以筛选和排序在内存中完成（单个教师的记录量在数百到数千条，开销很小）。
     */
    @Transactional(readOnly = true)
    public IPage<StudyRecordDTO> recordList(StudyRecordPagingQuery query) {
        int page = Optional.ofNullable(query.getPage()).filter(p -> p > 0).orElse(1);
        int size = Optional.ofNullable(query.getSize()).filter(s -> s > 0 && s <= 200).orElse(20);
        List<RecordRow> rows = filteredRows(query);
        Page<StudyRecordDTO> result = new Page<>(page, size, rows.size());
        result.setRecords(rows.stream().skip((long) (page - 1) * size).limit(size).map(RecordRow::toDTO).toList());
        return result;
    }

    /**
     * 按筛选条件返回全部记录 id（按列表顺序），用于批量打印报告和报告页的“上一份 / 下一份”
     */
    @Transactional(readOnly = true)
    public List<Integer> recordIds(StudyRecordPagingQuery query) {
        return filteredRows(query).stream().limit(MAX_BATCH).map(r -> r.record().getId()).toList();
    }

    private static final int MAX_BATCH = 1000;

    /**
     * 一条面试记录及其关联信息
     */
    private record RecordRow(StudentAssessmentRecordPO record, AssessmentSettingPO setting, UserPO user,
                             AssessmentAppointmentSettingPO session, Integer aiScore, Integer finalScore) {

        Boolean passed() {
            return finalScore == null || setting.getPassScore() == null ? null : finalScore >= setting.getPassScore();
        }

        boolean reviewed() {
            return StringUtils.isNotBlank(record.getTeacherReview());
        }

        StudyRecordDTO toDTO() {
            return StudyRecordDTO.builder()
                    .id(record.getId())
                    .assessmentId(setting.getId())
                    .studentName(user.getName())
                    .theme(setting.getTheme())
                    .score(aiScore)
                    .aiScore(aiScore)
                    .checkScore(record.getCheckScore())
                    .totalScore(setting.getTotalScore())
                    .assessmentTime(Optional.ofNullable(record.getStartTime()).orElse(record.getCreateTime()))
                    .state(record.getState())
                    .finalScore(finalScore)
                    .reviewed(reviewed())
                    .passScore(setting.getPassScore())
                    .studentAccount(user.getAccount())
                    .schoolClass(user.getSchoolClass())
                    .sessionLabel(ExamSessionService.sessionLabel(session))
                    .build();
        }
    }

    private List<RecordRow> filteredRows(StudyRecordPagingQuery query) {
        TeacherSummary teacherSummary = userService.findTeacherSummaryById(StpUtil.getLoginIdAsInt())
                .orElseThrow(() -> new BusinessException(ErrorCodeEnums.USER_NOT_FOUND));
        List<Integer> scope = teacherSummary.getStudentIds();
        if (CollectionUtils.isEmpty(scope)) {
            return List.of();
        }
        List<Integer> themeIds = null;
        if (StringUtils.isNotBlank(query.getTheme())) {
            themeIds = assessmentSettingService.list(Wrappers.lambdaQuery(AssessmentSettingPO.class)
                            .like(AssessmentSettingPO::getTheme, query.getTheme().trim()))
                    .stream().map(AssessmentSettingPO::getId).toList();
            if (themeIds.isEmpty()) {
                return List.of();
            }
        }
        List<StudentAssessmentRecordPO> records = studentAssessmentService.list(Wrappers.lambdaQuery(StudentAssessmentRecordPO.class)
                .in(StudentAssessmentRecordPO::getStudentId, scope)
                .in(query.getState() == null, StudentAssessmentRecordPO::getState, 1, 2)
                .eq(query.getState() != null, StudentAssessmentRecordPO::getState, query.getState())
                .eq(query.getAssessmentId() != null, StudentAssessmentRecordPO::getAssessmentId, query.getAssessmentId())
                .in(themeIds != null, StudentAssessmentRecordPO::getAssessmentId, themeIds)
                .in(CollectionUtils.isNotEmpty(query.getRecordIds()), StudentAssessmentRecordPO::getId, query.getRecordIds()));
        if (records.isEmpty()) {
            return List.of();
        }
        Set<Integer> assessmentIds = records.stream().map(StudentAssessmentRecordPO::getAssessmentId).collect(Collectors.toSet());
        Set<Integer> studentIds = records.stream().map(StudentAssessmentRecordPO::getStudentId).collect(Collectors.toSet());
        Map<Integer, AssessmentSettingPO> settings = assessmentSettingService.listByIds(assessmentIds).stream()
                .collect(Collectors.toMap(AssessmentSettingPO::getId, item -> item));
        Map<Integer, UserPO> users = userService.listByIds(studentIds).stream()
                .collect(Collectors.toMap(UserPO::getId, item -> item));
        Map<String, Integer> sessionIdOf = appointmentService.list(Wrappers.lambdaQuery(StudentAssessmentAppointmentPO.class)
                        .in(StudentAssessmentAppointmentPO::getAssessmentId, assessmentIds)
                        .in(StudentAssessmentAppointmentPO::getStudentId, studentIds)
                        .isNotNull(StudentAssessmentAppointmentPO::getSettingId)
                        .orderByAsc(StudentAssessmentAppointmentPO::getId))
                .stream().collect(Collectors.toMap(StudentAssessmentAppointmentPO::assessmentIdAndStudentId,
                        StudentAssessmentAppointmentPO::getSettingId, (a, b) -> b));
        Map<Integer, AssessmentAppointmentSettingPO> sessions = sessionIdOf.isEmpty() ? Map.of()
                : appointmentSettingService.listByIds(new HashSet<>(sessionIdOf.values())).stream()
                .collect(Collectors.toMap(AssessmentAppointmentSettingPO::getId, item -> item));

        String keyword = StringUtils.trimToNull(query.getStudentName());
        List<RecordRow> rows = new ArrayList<>();
        for (StudentAssessmentRecordPO record : records) {
            AssessmentSettingPO setting = settings.get(record.getAssessmentId());
            UserPO user = users.get(record.getStudentId());
            if (setting == null || user == null) {
                continue;
            }
            if (keyword != null && !StringUtils.contains(user.getName(), keyword)
                    && !StringUtils.contains(user.getAccount(), keyword)) {
                continue;
            }
            if (StringUtils.isNotBlank(query.getSchoolClass()) && !Objects.equals(user.getSchoolClass(), query.getSchoolClass())) {
                continue;
            }
            Integer sessionId = sessionIdOf.get(record.assessmentIdAndStudentId());
            if (query.getSessionId() != null && !Objects.equals(sessionId, query.getSessionId())) {
                continue;
            }
            RecordRow row = new RecordRow(record, setting, user, sessionId == null ? null : sessions.get(sessionId),
                    ScoreCalculator.aiScore(record), ScoreCalculator.finalScore(record));
            if (query.getMinScore() != null && (row.finalScore() == null || row.finalScore() < query.getMinScore())) {
                continue;
            }
            if (query.getMaxScore() != null && (row.finalScore() == null || row.finalScore() > query.getMaxScore())) {
                continue;
            }
            if (query.getPassed() != null && !Objects.equals(row.passed(), query.getPassed())) {
                continue;
            }
            if (query.getReviewed() != null && row.reviewed() != query.getReviewed()) {
                continue;
            }
            rows.add(row);
        }
        boolean asc = "asc".equalsIgnoreCase(query.getSortOrder());
        Comparator<RecordRow> byTime = Comparator.comparing(
                r -> Optional.ofNullable(r.record().getStartTime()).orElse(r.record().getCreateTime()),
                Comparator.nullsFirst(Comparator.naturalOrder()));
        Comparator<RecordRow> order = "score".equalsIgnoreCase(query.getSortField())
                ? Comparator.comparing(RecordRow::finalScore, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(byTime)
                : byTime;
        rows.sort(asc ? order : order.reversed());
        return rows;
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

    /**
     * 导出成绩 Excel：指定 recordIds 时导出这些记录，否则导出当前筛选条件下的全部记录。
     * 每个评分维度单独一列，便于在 Excel 中按维度统计。
     */
    @Transactional(readOnly = true)
    public void exportRecordExcel(StudyRecordPagingQuery query) {
        List<RecordRow> rows = filteredRows(query);
        // 维度列：按出现顺序汇总所有记录的维度
        Map<String, Integer> dimensionMax = new LinkedHashMap<>();
        Map<Integer, Map<String, Integer>> dimensionScores = new HashMap<>();
        for (RecordRow row : rows) {
            AssessmentEvaluationDTO evaluation = ScoreCalculator.evaluation(row.record());
            TeacherReviewBO review = StudentAssessmentService.parseReview(row.record().getTeacherReview());
            Map<String, Integer> reviewed = review == null || review.getDimensions() == null ? Map.of()
                    : review.getDimensions().stream().filter(d -> d.getName() != null && d.getScore() != null)
                    .collect(Collectors.toMap(TeacherReviewBO.DimensionScore::getName,
                            TeacherReviewBO.DimensionScore::getScore, (a, b) -> b));
            Map<String, Integer> scores = new HashMap<>();
            for (AnalysisDTO d : ScoreCalculator.dimensions(evaluation)) {
                if (d.getName() == null) {
                    continue;
                }
                dimensionMax.merge(d.getName(), d.getMaxScore() == null ? 0 : d.getMaxScore(), Math::max);
                scores.put(d.getName(), reviewed.getOrDefault(d.getName(), d.getScore()));
            }
            dimensionScores.put(row.record().getId(), scores);
        }
        DateTimeFormatter time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<Map<String, Object>> sheet = new ArrayList<>();
        int no = 0;
        for (RecordRow row : rows) {
            StudentAssessmentRecordPO record = row.record();
            AssessmentEvaluationDTO evaluation = ScoreCalculator.evaluation(record);
            TeacherReviewBO review = StudentAssessmentService.parseReview(record.getTeacherReview());
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("序号", ++no);
            line.put("姓名", row.user().getName());
            line.put("学号", row.user().getAccount());
            line.put("班级", row.user().getSchoolClass());
            line.put("面试名称", row.setting().getTheme());
            line.put("场次", ExamSessionService.sessionLabel(row.session()));
            LocalDateTime start = Optional.ofNullable(record.getStartTime()).orElse(record.getCreateTime());
            line.put("面试时间", start == null ? "" : start.format(time));
            line.put("状态", Objects.equals(record.getState(), 1) ? "已完成" : "评分中");
            Map<String, Integer> scores = dimensionScores.getOrDefault(record.getId(), Map.of());
            dimensionMax.forEach((name, max) -> line.put(max > 0 ? name + "（" + max + "分）" : name,
                    scores.containsKey(name) ? scores.get(name) : ""));
            line.put("AI 评分", row.aiScore() == null ? "" : row.aiScore());
            line.put("最终得分", row.finalScore() == null ? "" : row.finalScore());
            line.put("满分", row.setting().getTotalScore());
            line.put("及格分", row.setting().getPassScore());
            line.put("是否及格", row.passed() == null ? "" : (row.passed() ? "及格" : "不及格"));
            line.put("已复核", row.reviewed() ? "是" : "否");
            line.put("复核教师", review == null ? "" : StringUtils.defaultString(review.getReviewerName()));
            line.put("教师评语", review == null ? "" : StringUtils.defaultString(review.getComment()));
            line.put("整体评价", evaluation == null ? "" : StringUtils.defaultString(evaluation.getSummary()));
            sheet.add(line);
        }
        ExcelUtils.exportRows(sheet, "面试成绩");
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
