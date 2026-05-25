package system.assessment.defense.interfaces.rest;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import system.assessment.defense.application.manage.GeminiManager;
import system.assessment.defense.application.manage.OpenAiManager;
import system.assessment.defense.application.manage.TtsManager;
import system.assessment.defense.application.service.StudentAssessmentService;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentSettingPO;
import system.assessment.defense.infrastructure.repository.dao.service.AssessmentSettingService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/gemini")
@Slf4j
public class GeminiController {

    @Resource
    private GeminiManager geminiManager;

    @Resource
    private OpenAiManager openAiManager;

    @Resource
    private TtsManager ttsManager;

    @Resource
    private AssessmentSettingService assessmentSettingService;

    @Resource
    private StudentAssessmentService studentAssessmentService;

    @GetMapping("/question")
    public String test(@RequestParam("question") String question) {
        return null;
    }

    @PostMapping("/pickUpFileContent")
    public String pickUpFileContent(MultipartFile file) throws IOException {
        byte[] pdfBytes = file.getInputStream().readAllBytes();
        List<OpenAiManager.FileInput> files = List.of(
                new OpenAiManager.FileInput(pdfBytes, "application/pdf"));
        return openAiManager.chatCompletionWithFiles(
                openAiManager.getTextModel(),
                "你是一位专业的文档分析专家，请识别文件中的内容并输出，使用中文回答。",
                "识别文件中的内容并输出",
                files
        );
    }

    @PostMapping(value = "/pickUpVoice", consumes = "multipart/form-data")
    public String pickUpVoice(@RequestParam("file") MultipartFile file) throws IOException {
        byte[] bytes = file.getInputStream().readAllBytes();
        return geminiManager.pickVoiceContent(bytes);
    }

    @GetMapping("/generate-voice")
    public String generateVoice(@RequestParam("text") String text) throws IOException {
        String voice = ttsManager.generateVoice(text);
        return voice;
    }

    @GetMapping("/test-generate-question")
    public String testGenerateQuestion() {
        String systemPrompt = """
                你是一名专业的大学考核教授，负责根据上下文以及用户的输入生成高质量的考核题目，你的任务是：

                1. 严格基于用户提供的背景知识（包括 PDF 内容或知识库）出题；
                2. 所有题目必须紧扣提供的知识内容，不得编造超出范围的信息；
                3. **即使多次调用，也应生成结构、角度、表述均不同的题目组合；**
                4. 题目不得添加旁白等无效内容；
                5. 输出格式必须为标准 JSON。
                6. 如果不追问，则返回followQuestion为空

                JSON格式如下:
                {
                  "allDimensionQuestion":[{
                    "questionDimension":"问题维度",
                    "allQuestions":[{
                      "question":"问题",
                      "followQuestion":"追问问题"
                    }]
                  }]
                }

                注意：必须严格使用以上字段名称（allDimensionQuestion、questionDimension、allQuestions、question、followQuestion），不得使用其他字段名。
                """;
        return openAiManager.chatCompletionWithSchema(
                openAiManager.getTextModel(),
                systemPrompt,
                "考核标准：计算机科学基础\n背景知识：数据结构与算法\n是否使用题库：否\n是否追问：是\n请生成2个维度，每个维度2道题目",
                "dimension_questions",
                "{}"
        );
    }

    @GetMapping("/last-question")
    public String lastQuestion() {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "生成一个100字的笑话"));
        messages.add(Map.of("role", "assistant", "content",
                "这是一个100字的笑话，哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈哈"));
        messages.add(Map.of("role", "user", "content", "我的上一个问题是什么"));
        return openAiManager.chatCompletionMultiTurn(openAiManager.getTextModel(), messages);
    }
}
