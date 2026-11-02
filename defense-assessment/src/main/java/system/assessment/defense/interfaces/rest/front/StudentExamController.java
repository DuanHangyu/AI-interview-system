package system.assessment.defense.interfaces.rest.front;

import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import system.assessment.defense.application.dto.exam.ExamDTO;
import system.assessment.defense.application.service.exam.ExamSessionService;

@RestController
@RequestMapping("/front/exam")
@RequiredArgsConstructor
@Tag(name = "学生端：我的面试")
public class StudentExamController {

    private final ExamSessionService examSessionService;

    @GetMapping("/my-interviews")
    @Operation(summary = "我的全部面试及状态（场次时间、能否进入）")
    public ExamDTO.MyInterviews myInterviews() {
        return examSessionService.myInterviews(StpUtil.getLoginIdAsInt());
    }
}
