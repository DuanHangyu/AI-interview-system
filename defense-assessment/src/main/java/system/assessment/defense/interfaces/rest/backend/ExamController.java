package system.assessment.defense.interfaces.rest.backend;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import system.assessment.defense.application.dto.exam.ExamDTO;
import system.assessment.defense.application.service.exam.ExamSessionService;

import java.util.List;

@RestController
@RequestMapping("/backend/exam")
@RequiredArgsConstructor
@Tag(name = "考试场次与考场监控")
public class ExamController {

    private final ExamSessionService examSessionService;

    @GetMapping("/sessions")
    @Operation(summary = "场次列表")
    public List<ExamDTO.Session> sessions(@RequestParam("assessmentId") Integer assessmentId) {
        return examSessionService.listSessions(assessmentId);
    }

    @PostMapping("/sessions/save")
    @Operation(summary = "保存全部场次")
    public List<ExamDTO.Session> saveSessions(@Valid @RequestBody ExamDTO.SaveSessionsCmd cmd) {
        return examSessionService.saveSessions(cmd);
    }

    @PostMapping("/assign")
    @Operation(summary = "安排学生到场次（sessionId 为空表示取消安排）")
    public ExamDTO.AssignResult assign(@Valid @RequestBody ExamDTO.AssignCmd cmd) {
        return examSessionService.assign(cmd);
    }

    @PostMapping("/auto-assign")
    @Operation(summary = "把未安排的学生按班级、学号依次分到各场次")
    public ExamDTO.AssignResult autoAssign(@Valid @RequestBody ExamDTO.AssessmentCmd cmd) {
        return examSessionService.autoAssign(cmd.getAssessmentId());
    }

    @GetMapping("/monitor")
    @Operation(summary = "考场监控：全部学生的状态与答题进度")
    public ExamDTO.Monitor monitor(@RequestParam("assessmentId") Integer assessmentId) {
        return examSessionService.monitor(assessmentId);
    }
}
