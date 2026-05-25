package system.assessment.defense.application.service;

import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import system.assessment.defense.application.dto.FileDTO;
import system.assessment.defense.application.manage.OpenAiManager;
import system.assessment.defense.infrastructure.common.FileByteDTO;
import system.assessment.defense.infrastructure.common.HttpUtils;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentSettingPO;
import system.assessment.defense.infrastructure.repository.dao.service.AssessmentSettingService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentMaterialServiceTest {

    @Mock
    private HttpUtils httpUtils;

    @Mock
    private OpenAiManager openAiManager;

    @Mock
    private AssessmentSettingService settingService;

    private AssessmentMaterialService materialService;
    private ThreadPoolExecutor threadPoolExecutor;

    @BeforeEach
    void setUp() {
        threadPoolExecutor = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
        materialService = new AssessmentMaterialService(httpUtils, openAiManager, settingService, threadPoolExecutor);
    }

    @AfterEach
    void tearDown() {
        threadPoolExecutor.shutdownNow();
    }

    @Test
    void usesCachedMaterialContextWithoutDownloadingPdfAgain() {
        FileDTO file = new FileDTO();
        file.setFileName("design.pdf");
        file.setFileUrl("oss/design.pdf");
        file.setMaterialStatus(AssessmentMaterialService.STATUS_READY);
        file.setMaterialText("项目背景：学生完成了一个 AI 面试系统。");

        AssessmentSettingPO setting = AssessmentSettingPO.builder()
                .id(7)
                .assessmentFiles(JSONUtil.toJsonStr(List.of(file)))
                .build();

        String context = materialService.getOrParseMaterialContext(setting);

        assertThat(context).contains("design.pdf", "AI 面试系统");
        verifyNoInteractions(httpUtils, openAiManager, settingService);
    }

    @Test
    void parsesAndCachesMaterialContextWhenMissing() {
        FileDTO file = new FileDTO();
        file.setFileName("design.pdf");
        file.setFileUrl("oss/design.pdf");

        AssessmentSettingPO setting = AssessmentSettingPO.builder()
                .id(7)
                .assessmentFiles(JSONUtil.toJsonStr(List.of(file)))
                .build();

        byte[] pdfBytes = "fake pdf".getBytes(StandardCharsets.UTF_8);
        when(httpUtils.downloadFile("oss/design.pdf")).thenReturn(new FileByteDTO(pdfBytes));
        when(openAiManager.getTextModel()).thenReturn("qwen3.6-plus");
        when(openAiManager.chatCompletionWithFiles(eq("qwen3.6-plus"), anyString(), anyString(), any()))
                .thenReturn("项目背景：缓存后的材料摘要。");

        String context = materialService.getOrParseMaterialContext(setting);

        assertThat(context).contains("design.pdf", "缓存后的材料摘要");
        assertThat(setting.getAssessmentFiles()).contains(AssessmentMaterialService.STATUS_READY, "缓存后的材料摘要");
        verify(settingService).update(any());
    }

    @Test
    void returnsEmptyContextWhenThereAreNoAssessmentFiles() {
        AssessmentSettingPO setting = AssessmentSettingPO.builder()
                .id(7)
                .assessmentFiles("")
                .build();

        String context = materialService.getOrParseMaterialContext(setting);

        assertThat(context).isEmpty();
        verify(httpUtils, never()).downloadFile(anyString());
        verifyNoInteractions(openAiManager, settingService);
    }
}
