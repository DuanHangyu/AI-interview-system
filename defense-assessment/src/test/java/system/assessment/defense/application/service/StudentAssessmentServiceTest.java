package system.assessment.defense.application.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import system.assessment.defense.application.dto.FileDTO;
import system.assessment.defense.application.dto.StudentDefenseFileUploadCmd;
import system.assessment.defense.application.dto.ValueDTO;
import system.assessment.defense.application.task.TaskQueue;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentRecordPO;
import system.assessment.defense.infrastructure.repository.dao.service.AssessmentAppointmentSettingService;
import system.assessment.defense.infrastructure.repository.dao.service.AssessmentSettingService;
import system.assessment.defense.infrastructure.repository.dao.service.StudentAssessmentAppointmentService;
import system.assessment.defense.infrastructure.repository.dao.service.StudentAssessmentQuestionAnswerService;
import system.assessment.defense.infrastructure.repository.dao.service.StudentAssessmentRecordService;
import system.assessment.defense.infrastructure.repository.dao.service.UserService;
import system.assessment.defense.infrastructure.storage.FileStorage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentAssessmentServiceTest {

    @BeforeAll
    static void initTableInfo() {
        // 单元测试不启动 MyBatis，手动注册实体元数据，否则 lambda 条件构造器无法解析列名
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), StudentAssessmentRecordPO.class);
    }


    @Test
    void resolvesDisplayScoreWhenTeacherScoreIsBlank() {
        List<ValueDTO> values = List.of(
                new ValueDTO("指标A", 10),
                new ValueDTO("指标B", 20)
        );

        assertThat(StudentAssessmentService.resolveDisplayScore(null, values)).isEqualTo(30);
        assertThat(StudentAssessmentService.resolveDisplayScore(0, values)).isEqualTo(30);
        assertThat(StudentAssessmentService.resolveDisplayScore(25, values)).isEqualTo(25);
    }


    @Test
    void retakeRecordMustNotSkipDefenseIntro() {
        StudentAssessmentRecordPO retakeRecord = StudentAssessmentRecordPO.builder()
                .state(3)
                .endDefenseTime(LocalDateTime.now())
                .build();
        StudentAssessmentRecordPO currentRecord = StudentAssessmentRecordPO.builder()
                .state(0)
                .endDefenseTime(LocalDateTime.now())
                .build();
        StudentAssessmentRecordPO legacyCurrentRecord = StudentAssessmentRecordPO.builder()
                .endDefenseTime(LocalDateTime.now())
                .build();
        StudentAssessmentRecordPO doneRecord = StudentAssessmentRecordPO.builder()
                .state(1)
                .endDefenseTime(LocalDateTime.now())
                .build();

        assertThat(StudentAssessmentService.isDefenseFinishedForCurrentTodo(retakeRecord)).isFalse();
        assertThat(StudentAssessmentService.isDefenseFinishedForCurrentTodo(currentRecord)).isTrue();
        assertThat(StudentAssessmentService.isDefenseFinishedForCurrentTodo(legacyCurrentRecord)).isTrue();
        assertThat(StudentAssessmentService.isDefenseFinishedForCurrentTodo(doneRecord)).isFalse();
    }

    @Test
    void uploadDefenseFileUpdatesExistingRecord() {
        StudentAssessmentRecordService recordService = mock(StudentAssessmentRecordService.class);
        StudentAssessmentService service = newStudentAssessmentService(recordService);
        FileDTO file = new FileDTO();
        file.setFileUrl("defense-assessment-new/test/demo.pdf");
        file.setFileName("demo.pdf");
        file.setFileSize("1024");
        StudentDefenseFileUploadCmd cmd = new StudentDefenseFileUploadCmd();
        cmd.setAssessmentId(40);
        cmd.setFile(file);

        when(recordService.findByStudentIdAndAssessmentId(5, 40))
                .thenReturn(Optional.of(StudentAssessmentRecordPO.builder()
                        .studentId(5)
                        .assessmentId(40)
                        .build()));
        when(recordService.update(any(Wrapper.class))).thenReturn(true);

        assertThat(service.uploadDefenseFile(cmd, 5)).isTrue();

        ArgumentCaptor<Wrapper<StudentAssessmentRecordPO>> wrapperCaptor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(recordService).update(wrapperCaptor.capture());
        verify(recordService, never()).saveOrUpdate(any(StudentAssessmentRecordPO.class));
        LambdaUpdateWrapper<StudentAssessmentRecordPO> wrapper =
                (LambdaUpdateWrapper<StudentAssessmentRecordPO>) wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSet()).contains("defense_file");
    }

    @Test
    void uploadDefenseFileCreatesRecordWithNonNullDefaults() {
        StudentAssessmentRecordService recordService = mock(StudentAssessmentRecordService.class);
        StudentAssessmentService service = newStudentAssessmentService(recordService);
        FileDTO file = new FileDTO();
        file.setFileUrl("defense-assessment-new/test/demo.pdf");
        file.setFileName("demo.pdf");
        file.setFileSize("1024");
        StudentDefenseFileUploadCmd cmd = new StudentDefenseFileUploadCmd();
        cmd.setAssessmentId(40);
        cmd.setFile(file);

        when(recordService.findByStudentIdAndAssessmentId(5, 40))
                .thenReturn(Optional.empty());
        when(recordService.saveOrUpdate(any(StudentAssessmentRecordPO.class))).thenReturn(true);

        assertThat(service.uploadDefenseFile(cmd, 5)).isTrue();

        ArgumentCaptor<StudentAssessmentRecordPO> recordCaptor =
                ArgumentCaptor.forClass(StudentAssessmentRecordPO.class);
        verify(recordService).saveOrUpdate(recordCaptor.capture());
        StudentAssessmentRecordPO record = recordCaptor.getValue();
        assertThat(record.getDefenseFile()).contains("demo.pdf");
        assertThat(record.getDefenseVoice()).isEqualTo("");
        assertThat(record.getCheckScore()).isZero();
        assertThat(record.getState()).isZero();
        assertThat(record.getReAnalysis()).isFalse();
    }

    private StudentAssessmentService newStudentAssessmentService(StudentAssessmentRecordService recordService) {
        return new StudentAssessmentService(
                recordService,
                mock(UserService.class),
                mock(AssessmentSettingService.class),
                mock(AssessmentAppointmentSettingService.class),
                mock(FileStorage.class),
                mock(StudentAssessmentQuestionAnswerService.class),
                mock(StudentAssessmentAppointmentService.class),
                mock(TaskQueue.class)
        );
    }
}
