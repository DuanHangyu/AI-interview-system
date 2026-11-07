package system.assessment.defense.interfaces.rest.front;

import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import system.assessment.defense.application.dto.interview.InterviewCmd;
import system.assessment.defense.application.dto.interview.InterviewStateDTO;
import system.assessment.defense.application.service.interview.InterviewService;
import system.assessment.defense.infrastructure.common.Mp3Encoder;

import java.io.IOException;

/**
 * 学生端面试接口。全部是普通 HTTP 请求，可以安全重试；进度保存在服务端，刷新页面即可恢复。
 */
@RestController
@RequestMapping("/front/interview")
@Tag(name = "学生面试", description = "预制题目的语音面试流程")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @GetMapping("/state")
    @Operation(summary = "获取面试进度")
    public InterviewStateDTO state(@RequestParam("assessmentId") Integer assessmentId) {
        return interviewService.state(StpUtil.getLoginIdAsInt(), assessmentId);
    }

    @PostMapping("/start")
    @Operation(summary = "开始 / 继续面试（首次调用时抽题）")
    public InterviewStateDTO start(@RequestBody InterviewCmd cmd) {
        return interviewService.start(StpUtil.getLoginIdAsInt(), cmd.getAssessmentId());
    }

    @PostMapping("/defense")
    @Operation(summary = "上传答辩录音（WAV）")
    public InterviewStateDTO defense(@RequestParam("assessmentId") Integer assessmentId,
                                     @RequestParam("audio") MultipartFile audio) {
        return interviewService.uploadDefense(StpUtil.getLoginIdAsInt(), assessmentId, audio);
    }

    @PostMapping("/answer")
    @Operation(summary = "上传一道题（或追问）的回答录音（WAV）")
    public InterviewStateDTO answer(@RequestParam("assessmentId") Integer assessmentId,
                                    @RequestParam("index") Integer index,
                                    @RequestParam(value = "followUp", defaultValue = "false") boolean followUp,
                                    @RequestParam(value = "followIndex", required = false) Integer followIndex,
                                    @RequestParam("audio") MultipartFile audio) {
        return interviewService.uploadAnswer(StpUtil.getLoginIdAsInt(), assessmentId, index,
                followIndex(followUp, followIndex), audio);
    }

    @PostMapping("/submit")
    @Operation(summary = "提交面试，进入评分")
    public InterviewStateDTO submit(@RequestBody InterviewCmd cmd) {
        return interviewService.submit(StpUtil.getLoginIdAsInt(), cmd.getAssessmentId());
    }

    @GetMapping("/question-audio")
    @Operation(summary = "当前题目的播报音频")
    public void questionAudio(@RequestParam("assessmentId") Integer assessmentId,
                              @RequestParam("index") Integer index,
                              @RequestParam(value = "followUp", defaultValue = "false") boolean followUp,
                              @RequestParam(value = "followIndex", required = false) Integer followIndex,
                              HttpServletResponse response) throws IOException {
        byte[] wav = interviewService.questionAudio(StpUtil.getLoginIdAsInt(), assessmentId, index,
                followIndex(followUp, followIndex));
        Mp3Encoder.Audio audio = Mp3Encoder.fromWav(wav);
        response.setContentType(audio.contentType());
        response.setHeader("Cache-Control", "private, max-age=600");
        response.setContentLength(audio.data().length);
        response.getOutputStream().write(audio.data());
    }

    /**
     * followIndex：0 主问题，1、2… 第几个追问；兼容只传 followUp=true 的旧前端（视为第 1 个追问）
     */
    private static int followIndex(boolean followUp, Integer followIndex) {
        if (followIndex != null) {
            return Math.max(0, followIndex);
        }
        return followUp ? 1 : 0;
    }
}
