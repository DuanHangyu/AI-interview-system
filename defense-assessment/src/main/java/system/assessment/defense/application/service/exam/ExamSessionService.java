package system.assessment.defense.application.service.exam;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import system.assessment.defense.application.dto.exam.ExamDTO;
import system.assessment.defense.application.dto.interview.InterviewStateDTO;
import system.assessment.defense.application.service.interview.InterviewService;
import system.assessment.defense.infrastructure.emuns.ErrorCodeEnums;
import system.assessment.defense.infrastructure.exception.BusinessException;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentAppointmentSettingPO;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentInvitePO;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentSettingPO;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentStudentRelationPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentAppointmentPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentQuestionAnswerPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentRecordPO;
import system.assessment.defense.infrastructure.repository.dao.po.UserPO;
import system.assessment.defense.infrastructure.repository.dao.service.AssessmentAppointmentSettingService;
import system.assessment.defense.infrastructure.repository.dao.service.AssessmentInviteService;
import system.assessment.defense.infrastructure.repository.dao.service.AssessmentSettingService;
import system.assessment.defense.infrastructure.repository.dao.service.AssessmentStudentRelationService;
import system.assessment.defense.infrastructure.repository.dao.service.StudentAssessmentAppointmentService;
import system.assessment.defense.infrastructure.repository.dao.service.StudentAssessmentQuestionAnswerService;
import system.assessment.defense.infrastructure.repository.dao.service.StudentAssessmentRecordService;
import system.assessment.defense.infrastructure.repository.dao.service.UserService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 考试场次：老师设置场次（开始、结束时间）并把学生安排到场次，学生只能在自己场次的时间内开始面试；
 * 考场监控：实时查看每个学生的状态和答题进度。
 * <p>
 * 场次复用 assessment_appointment_setting，学生的安排复用 student_assessment_appointment（每人每场面试一行）。
 * 状态（待考、缺考等）按时间实时计算，不再由定时任务修改，迟到没有惩罚，老师可以随时改到其他场次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamSessionService {

    private static final int ADMIN = 2;
    private static final int RECORD_DONE = 1;
    private static final int RECORD_ANALYZING = 2;
    private static final DateTimeFormatter LABEL_TIME = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    private final AssessmentSettingService settingService;
    private final AssessmentAppointmentSettingService sessionService;
    private final StudentAssessmentAppointmentService assignmentService;
    private final AssessmentStudentRelationService relationService;
    private final AssessmentInviteService inviteService;
    private final StudentAssessmentRecordService recordService;
    private final StudentAssessmentQuestionAnswerService answerService;
    private final UserService userService;

    // ------------------------------------------------------------------ 权限

    /**
     * 管理员可以管理全部面试；教师只能管理自己创建或受邀协作的面试
     */
    public AssessmentSettingPO checkAccess(Integer assessmentId) {
        AssessmentSettingPO setting = settingService.findById(assessmentId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnums.ASSESSMENT_SETTING_NOT_FOUND));
        int userId = StpUtil.getLoginIdAsInt();
        if (Objects.equals(setting.getCreateId(), userId)) {
            return setting;
        }
        UserPO user = userService.getById(userId);
        if (user != null && Objects.equals(user.getType(), ADMIN)) {
            return setting;
        }
        boolean invited = inviteService.count(Wrappers.lambdaQuery(AssessmentInvitePO.class)
                .eq(AssessmentInvitePO::getAssessmentId, assessmentId)
                .eq(AssessmentInvitePO::getInviteeTeacherId, userId)) > 0;
        if (!invited) {
            throw new BusinessException(403, "无权管理该面试");
        }
        return setting;
    }

    // ------------------------------------------------------------------ 场次

    public List<ExamDTO.Session> listSessions(Integer assessmentId) {
        checkAccess(assessmentId);
        return toSessionDTOs(sessionsOf(assessmentId), assignmentsOf(assessmentId));
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ExamDTO.Session> saveSessions(ExamDTO.SaveSessionsCmd cmd) {
        AssessmentSettingPO setting = checkAccess(cmd.getAssessmentId());
        List<ExamDTO.Session> incoming = Optional.ofNullable(cmd.getSessions()).orElse(List.of());
        for (int i = 0; i < incoming.size(); i++) {
            ExamDTO.Session s = incoming.get(i);
            if (s.getStartTime() == null || s.getEndTime() == null) {
                throw new BusinessException(400, "第 " + (i + 1) + " 个场次的开始和结束时间不能为空");
            }
            if (!s.getEndTime().isAfter(s.getStartTime())) {
                throw new BusinessException(400, "第 " + (i + 1) + " 个场次的结束时间必须晚于开始时间");
            }
            if (s.getCapacity() != null && s.getCapacity() < 1) {
                throw new BusinessException(400, "第 " + (i + 1) + " 个场次的人数上限至少为 1");
            }
        }
        Integer assessmentId = setting.getId();
        Map<Integer, AssessmentAppointmentSettingPO> existing = sessionsOf(assessmentId).stream()
                .collect(Collectors.toMap(AssessmentAppointmentSettingPO::getId, Function.identity()));
        Set<Integer> kept = new HashSet<>();
        for (ExamDTO.Session s : incoming) {
            AssessmentAppointmentSettingPO po;
            if (s.getId() != null) {
                po = existing.get(s.getId());
                if (po == null) {
                    throw new BusinessException(400, "场次不存在，请刷新后重试");
                }
                kept.add(po.getId());
            } else {
                po = new AssessmentAppointmentSettingPO();
                po.setAssessmentId(assessmentId);
            }
            po.setTimePeriod(s.getStartTime());
            po.setEndTime(s.getEndTime());
            po.setLocation(StringUtils.trimToNull(s.getLocation()));
            po.setParticipantLimit(s.getCapacity());
            if (po.getId() == null) {
                sessionService.save(po);
            } else {
                // updateById 会跳过空字段，清空人数上限、地点时必须显式写入 null
                sessionService.update(Wrappers.lambdaUpdate(AssessmentAppointmentSettingPO.class)
                        .set(AssessmentAppointmentSettingPO::getTimePeriod, po.getTimePeriod())
                        .set(AssessmentAppointmentSettingPO::getEndTime, po.getEndTime())
                        .set(AssessmentAppointmentSettingPO::getLocation, po.getLocation())
                        .set(AssessmentAppointmentSettingPO::getParticipantLimit, po.getParticipantLimit())
                        .eq(AssessmentAppointmentSettingPO::getId, po.getId()));
            }
            // 旧代码按开始时间关联学生，保持同步
            assignmentService.update(Wrappers.lambdaUpdate(StudentAssessmentAppointmentPO.class)
                    .set(StudentAssessmentAppointmentPO::getTimePeriod, po.getTimePeriod())
                    .eq(StudentAssessmentAppointmentPO::getSettingId, po.getId()));
        }
        List<Integer> removed = existing.keySet().stream().filter(id -> !kept.contains(id)).toList();
        if (!removed.isEmpty()) {
            // 删除场次：安排在这些场次里的学生变为未安排（已开考、已完成的学生不受影响）
            sessionService.removeByIds(removed);
            assignmentService.remove(Wrappers.lambdaQuery(StudentAssessmentAppointmentPO.class)
                    .eq(StudentAssessmentAppointmentPO::getAssessmentId, assessmentId)
                    .in(StudentAssessmentAppointmentPO::getSettingId, removed));
        }
        boolean bySession = !incoming.isEmpty();
        settingService.update(Wrappers.lambdaUpdate(AssessmentSettingPO.class)
                .set(AssessmentSettingPO::getNeedAppoint, bySession)
                .set(AssessmentSettingPO::getAssessmentFailPunish, false)
                .set(AssessmentSettingPO::getLastAppointTime, incoming.stream().map(ExamDTO.Session::getEndTime)
                        .max(Comparator.naturalOrder()).orElse(null))
                .eq(AssessmentSettingPO::getId, assessmentId));
        log.info("exam sessions saved, assessmentId:{}, sessions:{}, removed:{}", assessmentId, incoming.size(), removed.size());
        return toSessionDTOs(sessionsOf(assessmentId), assignmentsOf(assessmentId));
    }

    /**
     * 把学生安排到场次（sessionId 为空时取消安排）。已开考或已完成的学生不改动。
     */
    @Transactional(rollbackFor = Exception.class)
    public ExamDTO.AssignResult assign(ExamDTO.AssignCmd cmd) {
        AssessmentSettingPO setting = checkAccess(cmd.getAssessmentId());
        Integer assessmentId = setting.getId();
        List<Integer> requested = Optional.ofNullable(cmd.getStudentIds()).orElse(List.of()).stream()
                .filter(Objects::nonNull).distinct().toList();
        if (requested.isEmpty()) {
            throw new BusinessException(400, "请选择学生");
        }
        AssessmentAppointmentSettingPO session = null;
        if (cmd.getSessionId() != null) {
            session = sessionService.getById(cmd.getSessionId());
            if (session == null || !Objects.equals(session.getAssessmentId(), assessmentId)) {
                throw new BusinessException(400, "场次不存在，请刷新后重试");
            }
        }
        Set<Integer> roster = rosterOf(assessmentId);
        Map<Integer, StudentAssessmentRecordPO> records = recordsOf(assessmentId);
        List<Integer> movable = new ArrayList<>();
        int skipped = 0;
        for (Integer studentId : requested) {
            if (!roster.contains(studentId)) {
                throw new BusinessException(400, "所选学生不在本场面试名单中，请刷新后重试");
            }
            ExamStatus status = ExamStatus.of(true, records.get(studentId), null, LocalDateTime.now());
            if (status == ExamStatus.IN_PROGRESS || status == ExamStatus.ANALYZING || status == ExamStatus.DONE) {
                skipped++;
            } else {
                movable.add(studentId);
            }
        }
        if (session != null && session.getParticipantLimit() != null) {
            Integer sessionId = session.getId();
            long others = assignmentsOf(assessmentId).values().stream()
                    .filter(a -> Objects.equals(a.getSettingId(), sessionId) && !movable.contains(a.getStudentId()))
                    .count();
            if (others + movable.size() > session.getParticipantLimit()) {
                throw new BusinessException(400, String.format("该场次最多 %d 人，已安排 %d 人，本次还能再安排 %d 人",
                        session.getParticipantLimit(), others, Math.max(0, session.getParticipantLimit() - others)));
            }
        }
        if (!movable.isEmpty()) {
            assignmentService.remove(Wrappers.lambdaQuery(StudentAssessmentAppointmentPO.class)
                    .eq(StudentAssessmentAppointmentPO::getAssessmentId, assessmentId)
                    .in(StudentAssessmentAppointmentPO::getStudentId, movable));
            if (session != null) {
                LocalDateTime now = LocalDateTime.now();
                List<StudentAssessmentAppointmentPO> rows = new ArrayList<>();
                for (Integer studentId : movable) {
                    StudentAssessmentAppointmentPO row = new StudentAssessmentAppointmentPO();
                    row.setAssessmentId(assessmentId);
                    row.setStudentId(studentId);
                    row.setSettingId(session.getId());
                    row.setTimePeriod(session.getTimePeriod());
                    row.setAppointmentTime(now);
                    row.setState(0);
                    row.setPunishState(-1);
                    rows.add(row);
                }
                assignmentService.saveBatch(rows);
            }
        }
        log.info("exam assign, assessmentId:{}, sessionId:{}, assigned:{}, skipped:{}", assessmentId,
                cmd.getSessionId(), movable.size(), skipped);
        return ExamDTO.AssignResult.builder().assigned(movable.size()).skipped(skipped).build();
    }

    /**
     * 把所有未安排、未开考的学生按班级、学号顺序依次分到还没结束的场次，优先填满前面的场次；
     * 场次不限人数时平均分配。
     */
    @Transactional(rollbackFor = Exception.class)
    public ExamDTO.AssignResult autoAssign(Integer assessmentId) {
        checkAccess(assessmentId);
        LocalDateTime now = LocalDateTime.now();
        List<AssessmentAppointmentSettingPO> sessions = sessionsOf(assessmentId).stream()
                .filter(s -> s.getEndTime() == null || s.getEndTime().isAfter(now))
                .toList();
        if (sessions.isEmpty()) {
            throw new BusinessException(400, "没有尚未结束的场次，请先添加场次");
        }
        Map<Integer, StudentAssessmentAppointmentPO> assignments = assignmentsOf(assessmentId);
        Map<Integer, StudentAssessmentRecordPO> records = recordsOf(assessmentId);
        Map<Integer, AssessmentAppointmentSettingPO> sessionById = sessionsOf(assessmentId).stream()
                .collect(Collectors.toMap(AssessmentAppointmentSettingPO::getId, Function.identity()));
        List<UserPO> pending = usersOf(rosterOf(assessmentId)).values().stream()
                .filter(u -> {
                    StudentAssessmentAppointmentPO a = assignments.get(u.getId());
                    AssessmentAppointmentSettingPO s = a == null ? null : sessionById.get(a.getSettingId());
                    ExamStatus status = ExamStatus.of(true, records.get(u.getId()), s, now);
                    return status == ExamStatus.UNSCHEDULED;
                })
                .sorted(Comparator.comparing((UserPO u) -> StringUtils.defaultString(u.getSchoolClass()))
                        .thenComparing(u -> StringUtils.defaultString(u.getAccount())))
                .toList();
        if (pending.isEmpty()) {
            return ExamDTO.AssignResult.builder().assigned(0).skipped(0).build();
        }
        Map<Integer, Long> used = assignments.values().stream()
                .filter(a -> a.getSettingId() != null)
                .collect(Collectors.groupingBy(StudentAssessmentAppointmentPO::getSettingId, Collectors.counting()));
        boolean unlimited = sessions.stream().allMatch(s -> s.getParticipantLimit() == null);
        int evenShare = (int) Math.ceil(pending.size() / (double) sessions.size());
        int index = 0;
        int assigned = 0;
        for (AssessmentAppointmentSettingPO session : sessions) {
            int room = unlimited ? evenShare
                    : session.getParticipantLimit() == null ? Integer.MAX_VALUE
                    : (int) Math.max(0, session.getParticipantLimit() - used.getOrDefault(session.getId(), 0L));
            List<Integer> batch = new ArrayList<>();
            while (index < pending.size() && batch.size() < room) {
                batch.add(pending.get(index++).getId());
            }
            if (!batch.isEmpty()) {
                ExamDTO.AssignCmd cmd = new ExamDTO.AssignCmd();
                cmd.setAssessmentId(assessmentId);
                cmd.setSessionId(session.getId());
                cmd.setStudentIds(batch);
                assigned += assign(cmd).getAssigned();
            }
        }
        if (index < pending.size()) {
            throw new BusinessException(400, String.format("场次人数上限不够：还有 %d 名学生无法安排，请增加场次或提高人数上限",
                    pending.size() - index));
        }
        return ExamDTO.AssignResult.builder().assigned(assigned).skipped(0).build();
    }

    // ------------------------------------------------------------------ 考场监控

    public ExamDTO.Monitor monitor(Integer assessmentId) {
        AssessmentSettingPO setting = checkAccess(assessmentId);
        LocalDateTime now = LocalDateTime.now();
        boolean bySession = Boolean.TRUE.equals(setting.getNeedAppoint());
        List<AssessmentAppointmentSettingPO> sessions = sessionsOf(assessmentId);
        Map<Integer, AssessmentAppointmentSettingPO> sessionById = sessions.stream()
                .collect(Collectors.toMap(AssessmentAppointmentSettingPO::getId, Function.identity()));
        Map<Integer, StudentAssessmentAppointmentPO> assignments = assignmentsOf(assessmentId);
        Map<Integer, StudentAssessmentRecordPO> records = recordsOf(assessmentId);
        Map<Integer, List<StudentAssessmentQuestionAnswerPO>> answers = answerService.list(
                        Wrappers.lambdaQuery(StudentAssessmentQuestionAnswerPO.class)
                                .eq(StudentAssessmentQuestionAnswerPO::getAssessmentId, assessmentId))
                .stream().collect(Collectors.groupingBy(StudentAssessmentQuestionAnswerPO::getStudentId));

        List<ExamDTO.StudentRow> rows = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ExamStatus s : ExamStatus.values()) {
            counts.put(s.name(), 0);
        }
        for (UserPO user : usersOf(rosterOf(assessmentId)).values()) {
            StudentAssessmentRecordPO record = records.get(user.getId());
            StudentAssessmentAppointmentPO assignment = assignments.get(user.getId());
            AssessmentAppointmentSettingPO session = assignment == null ? null : sessionById.get(assignment.getSettingId());
            ExamStatus status = ExamStatus.of(bySession, record, session, now);
            counts.merge(status.name(), 1, Integer::sum);
            ExamDTO.StudentRow row = ExamDTO.StudentRow.builder()
                    .studentId(user.getId())
                    .name(user.getName())
                    .account(user.getAccount())
                    .schoolClass(user.getSchoolClass())
                    .sessionId(session == null ? null : session.getId())
                    .sessionStart(session == null ? null : session.getTimePeriod())
                    .sessionEnd(session == null ? null : session.getEndTime())
                    .location(session == null ? null : session.getLocation())
                    .status(status.name())
                    .statusLabel(status.label())
                    .retake(ExamStatus.isRetake(record))
                    .build();
            if (status == ExamStatus.IN_PROGRESS || status == ExamStatus.ANALYZING || status == ExamStatus.DONE) {
                fillProgress(row, setting, record, answers.getOrDefault(user.getId(), List.of()));
            }
            if (status == ExamStatus.DONE || status == ExamStatus.ANALYZING) {
                row.setRecordId(record.getId());
            }
            if (status == ExamStatus.DONE) {
                Integer score = ScoreCalculator.finalScore(record);
                row.setFinalScore(score);
                row.setPassed(score != null && setting.getPassScore() != null ? score >= setting.getPassScore() : null);
                row.setReviewed(StringUtils.isNotBlank(record.getTeacherReview()));
            }
            rows.add(row);
        }
        rows.sort(Comparator.comparing((ExamDTO.StudentRow r) -> Optional.ofNullable(r.getSessionStart()).orElse(LocalDateTime.MAX))
                .thenComparing(r -> StringUtils.defaultString(r.getSchoolClass()))
                .thenComparing(r -> StringUtils.defaultString(r.getAccount())));
        return ExamDTO.Monitor.builder()
                .assessmentId(assessmentId)
                .theme(setting.getTheme())
                .bySession(bySession)
                .totalScore(setting.getTotalScore())
                .passScore(setting.getPassScore())
                .serverTime(now)
                .sessions(toSessionDTOs(sessions, assignments))
                .counts(counts)
                .students(rows)
                .build();
    }

    private static void fillProgress(ExamDTO.StudentRow row, AssessmentSettingPO setting, StudentAssessmentRecordPO record,
                                     List<StudentAssessmentQuestionAnswerPO> answers) {
        row.setStartTime(record.getStartTime());
        int total = answers.size() + (Boolean.TRUE.equals(setting.getDefense()) ? 1 : 0);
        int answered = (int) answers.stream().filter(a -> a.getAnswerVoice() != null).count()
                + (record.getEndDefenseTime() != null ? 1 : 0);
        row.setTotalSteps(total);
        row.setAnsweredSteps(answered);
        row.setLastActiveTime(answers.stream()
                .filter(a -> a.getAnswerVoice() != null && a.getUpdateTime() != null)
                .map(StudentAssessmentQuestionAnswerPO::getUpdateTime)
                .max(Comparator.naturalOrder())
                .orElse(Optional.ofNullable(record.getEndDefenseTime()).orElse(record.getStartTime())));
        if (!Objects.equals(record.getState(), 0)) {
            return;
        }
        InterviewStateDTO state = InterviewService.buildState(setting, record, answers);
        if (InterviewStateDTO.DEFENSE.equals(state.getStatus())) {
            row.setProgress("项目答辩");
        } else if (state.getCurrent() != null) {
            InterviewStateDTO.CurrentQuestion c = state.getCurrent();
            String progress = "第 " + c.getIndex() + "/" + state.getTotal() + " 题";
            if (Boolean.TRUE.equals(c.getFollowUp())) {
                progress += " · 追问 " + c.getFollowIndex() + "/" + c.getFollowTotal();
            }
            row.setProgress(progress);
        } else if (InterviewStateDTO.READY_TO_SUBMIT.equals(state.getStatus())) {
            row.setProgress("待提交");
        }
    }

    // ------------------------------------------------------------------ 学生端

    public ExamDTO.MyInterviews myInterviews(Integer studentId) {
        LocalDateTime now = LocalDateTime.now();
        List<Integer> assessmentIds = relationService.list(Wrappers.lambdaQuery(AssessmentStudentRelationPO.class)
                        .eq(AssessmentStudentRelationPO::getStudentId, studentId))
                .stream().map(AssessmentStudentRelationPO::getAssessmentId).distinct().toList();
        if (assessmentIds.isEmpty()) {
            return ExamDTO.MyInterviews.builder().serverTime(now).items(List.of()).build();
        }
        List<AssessmentSettingPO> settings = settingService.listByIds(assessmentIds);
        Map<Integer, StudentAssessmentRecordPO> records = recordService.list(Wrappers.lambdaQuery(StudentAssessmentRecordPO.class)
                        .eq(StudentAssessmentRecordPO::getStudentId, studentId)
                        .in(StudentAssessmentRecordPO::getAssessmentId, assessmentIds))
                .stream().collect(Collectors.toMap(StudentAssessmentRecordPO::getAssessmentId, Function.identity(), (a, b) -> a));
        Map<Integer, StudentAssessmentAppointmentPO> assignments = assignmentService.list(
                        Wrappers.lambdaQuery(StudentAssessmentAppointmentPO.class)
                                .eq(StudentAssessmentAppointmentPO::getStudentId, studentId)
                                .in(StudentAssessmentAppointmentPO::getAssessmentId, assessmentIds))
                .stream().collect(Collectors.toMap(StudentAssessmentAppointmentPO::getAssessmentId, Function.identity(),
                        (a, b) -> a.getId() > b.getId() ? a : b));
        Set<Integer> sessionIds = assignments.values().stream().map(StudentAssessmentAppointmentPO::getSettingId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, AssessmentAppointmentSettingPO> sessions = sessionIds.isEmpty() ? Map.of()
                : sessionService.listByIds(sessionIds).stream()
                .collect(Collectors.toMap(AssessmentAppointmentSettingPO::getId, Function.identity()));

        List<ExamDTO.MyInterview> items = new ArrayList<>();
        for (AssessmentSettingPO setting : settings) {
            if (Boolean.TRUE.equals(setting.getDeleted())) {
                continue;
            }
            StudentAssessmentRecordPO record = records.get(setting.getId());
            StudentAssessmentAppointmentPO assignment = assignments.get(setting.getId());
            AssessmentAppointmentSettingPO session = assignment == null ? null : sessions.get(assignment.getSettingId());
            ExamStatus status = ExamStatus.of(Boolean.TRUE.equals(setting.getNeedAppoint()), record, session, now);
            items.add(ExamDTO.MyInterview.builder()
                    .assessmentId(setting.getId())
                    .theme(setting.getTheme())
                    .defense(setting.getDefense())
                    .question(setting.getQuestion())
                    .questionCount(setting.getQuestionCount())
                    .answerTime(setting.getAnswerTime())
                    .duration(setting.getDuration())
                    .totalScore(setting.getTotalScore())
                    .status(status.name())
                    .statusLabel(status.label())
                    .retake(ExamStatus.isRetake(record))
                    .sessionStart(session == null ? null : session.getTimePeriod())
                    .sessionEnd(session == null ? null : session.getEndTime())
                    .location(session == null ? null : session.getLocation())
                    .canEnter(status.canEnter(session, now))
                    .canStart(status.canStart())
                    .showResult(setting.getShowResult())
                    .build());
        }
        // 能进入的排在前面，其次按场次时间
        items.sort(Comparator.comparing((ExamDTO.MyInterview i) -> !Boolean.TRUE.equals(i.getCanEnter()))
                .thenComparing(i -> Optional.ofNullable(i.getSessionStart()).orElse(LocalDateTime.MAX))
                .thenComparing(ExamDTO.MyInterview::getAssessmentId, Comparator.reverseOrder()));
        return ExamDTO.MyInterviews.builder().serverTime(now).items(items).build();
    }

    /**
     * 学生能否开始面试；不能时返回原因。已开始的面试（断线重连）始终可以继续。
     */
    public String startBlockedReason(AssessmentSettingPO setting, Integer studentId, StudentAssessmentRecordPO record) {
        if (!Boolean.TRUE.equals(setting.getNeedAppoint())) {
            return null;
        }
        AssessmentAppointmentSettingPO session = sessionOf(setting.getId(), studentId);
        ExamStatus status = ExamStatus.of(true, record, session, LocalDateTime.now());
        return switch (status) {
            case UNSCHEDULED -> "老师还没有为你安排面试场次";
            case WAITING -> "未到面试时间，你的场次是 " + sessionLabel(session);
            case MISSED -> "你的场次（" + sessionLabel(session) + "）已结束，请联系老师重新安排";
            default -> null;
        };
    }

    public AssessmentAppointmentSettingPO sessionOf(Integer assessmentId, Integer studentId) {
        StudentAssessmentAppointmentPO assignment = assignmentService.list(Wrappers.lambdaQuery(StudentAssessmentAppointmentPO.class)
                        .eq(StudentAssessmentAppointmentPO::getAssessmentId, assessmentId)
                        .eq(StudentAssessmentAppointmentPO::getStudentId, studentId))
                .stream().max(Comparator.comparing(StudentAssessmentAppointmentPO::getId)).orElse(null);
        return assignment == null || assignment.getSettingId() == null ? null : sessionService.getById(assignment.getSettingId());
    }

    public static String sessionLabel(AssessmentAppointmentSettingPO session) {
        if (session == null) {
            return "";
        }
        String label = session.getTimePeriod().format(LABEL_TIME);
        if (session.getEndTime() != null) {
            label += "–" + session.getEndTime().format(DateTimeFormatter.ofPattern(
                    session.getEndTime().toLocalDate().equals(session.getTimePeriod().toLocalDate()) ? "HH:mm" : "M月d日 HH:mm"));
        }
        return label;
    }

    // ------------------------------------------------------------------ 查询工具

    private List<AssessmentAppointmentSettingPO> sessionsOf(Integer assessmentId) {
        return sessionService.list(Wrappers.lambdaQuery(AssessmentAppointmentSettingPO.class)
                .eq(AssessmentAppointmentSettingPO::getAssessmentId, assessmentId)
                .orderByAsc(AssessmentAppointmentSettingPO::getTimePeriod));
    }

    /**
     * studentId → 安排（同一学生有多行时取最新的一行）
     */
    private Map<Integer, StudentAssessmentAppointmentPO> assignmentsOf(Integer assessmentId) {
        return assignmentService.list(Wrappers.lambdaQuery(StudentAssessmentAppointmentPO.class)
                        .eq(StudentAssessmentAppointmentPO::getAssessmentId, assessmentId))
                .stream().collect(Collectors.toMap(StudentAssessmentAppointmentPO::getStudentId, Function.identity(),
                        (a, b) -> a.getId() > b.getId() ? a : b));
    }

    private Map<Integer, StudentAssessmentRecordPO> recordsOf(Integer assessmentId) {
        return recordService.list(Wrappers.lambdaQuery(StudentAssessmentRecordPO.class)
                        .eq(StudentAssessmentRecordPO::getAssessmentId, assessmentId))
                .stream().collect(Collectors.toMap(StudentAssessmentRecordPO::getStudentId, Function.identity(), (a, b) -> a));
    }

    private Set<Integer> rosterOf(Integer assessmentId) {
        return relationService.list(Wrappers.lambdaQuery(AssessmentStudentRelationPO.class)
                        .eq(AssessmentStudentRelationPO::getAssessmentId, assessmentId))
                .stream().map(AssessmentStudentRelationPO::getStudentId).collect(Collectors.toSet());
    }

    private Map<Integer, UserPO> usersOf(Set<Integer> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userService.listByIds(ids).stream().collect(Collectors.toMap(UserPO::getId, Function.identity()));
    }

    private static List<ExamDTO.Session> toSessionDTOs(List<AssessmentAppointmentSettingPO> sessions,
                                                       Map<Integer, StudentAssessmentAppointmentPO> assignments) {
        Map<Integer, Long> counts = assignments.values().stream()
                .filter(a -> a.getSettingId() != null)
                .collect(Collectors.groupingBy(StudentAssessmentAppointmentPO::getSettingId, Collectors.counting()));
        return sessions.stream()
                .map(s -> ExamDTO.Session.builder()
                        .id(s.getId())
                        .startTime(s.getTimePeriod())
                        .endTime(s.getEndTime())
                        .location(s.getLocation())
                        .capacity(s.getParticipantLimit())
                        .assignedCount(counts.getOrDefault(s.getId(), 0L).intValue())
                        .build())
                .toList();
    }
}
