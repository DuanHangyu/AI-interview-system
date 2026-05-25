package system.assessment.defense.interfaces.rest.backend;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import system.assessment.defense.application.dto.*;
import system.assessment.defense.application.dto.backed.AppointmentSettingCreateCmd;
import system.assessment.defense.application.dto.backed.AssessmentAppointSettingSummaryDTO;
import system.assessment.defense.application.dto.backed.CancelStudentAppointmentCmd;
import system.assessment.defense.application.dto.backed.LiftPunishCmd;
import system.assessment.defense.application.service.AssessmentAppService;

/**
 * @USER taoHouChao
 * @DATE 19:48 2025/8/7
 */
@RestController
@RequestMapping("/backend/assessment")
@Tag(name = "考核设置管理", description = "考核设置管理")
@Slf4j
public class AssessmentSettingsController {

    @Resource
    private AssessmentAppService assessmentAppService;

    @PostMapping("/list")
    @Operation(summary = "获取考核设置列表")
    public IPage<AssessmentSettingsDTO> list(@RequestBody AssessmentSettingPagingQuery pagingQuery) throws Exception {
        return assessmentAppService.list(StpUtil.getLoginIdAsInt(), pagingQuery);
    }

    @PostMapping("/create")
    @Operation(summary = "创建考核")
    public Boolean createAssessmentSettings(@RequestBody AssessmentSettingsCreateCmd createCmd) {
        return assessmentAppService.createAssessmentSettings(createCmd);
    }

    @PostMapping("/modify")
    @Operation(summary = "修改考核")
    public Boolean modifyAssessmentSettings(@RequestBody AssessmentSettingsModifyCmd modifyCmd) {
        return assessmentAppService.modifyAssessmentSettings(modifyCmd);
    }

    @PostMapping("/remove")
    @Operation(summary = "删除考核")
    public Boolean removeAssessmentSettings(@RequestBody AssessmentSettingsRemoveCmd deleteCmd) {
        return assessmentAppService.removeAssessmentSettings(deleteCmd);
    }

    @PostMapping("/settingAppointment")
    @Operation(summary = "设置考核预约")
    public Boolean settingAppointment(@RequestBody AppointmentSettingCreateCmd appointmentCmd) {
        return assessmentAppService.settingAppointment(appointmentCmd);
    }

    @GetMapping("/setting-appointment-summary")
    @Operation(summary = "获取考核预约设置汇总")
    public AssessmentAppointSettingSummaryDTO settingAppointmentSummary(@RequestParam("id") @Schema(description = "考核ID") Integer id) {
        return assessmentAppService.settingAppointmentSummary(id);
    }

    @PostMapping("/lift-punish")
    @Operation(summary = "解除惩罚")
    public Boolean liftPunish(@RequestBody LiftPunishCmd liftPunishCmd) {
        return assessmentAppService.liftPunish(liftPunishCmd);
    }

    @PostMapping("/cancel-student-appointment")
    @Operation(summary = "取消学生预约")
    public Boolean cancelStudentAppointment(@RequestBody CancelStudentAppointmentCmd cancelStudentAppointmentCmd) {
        return assessmentAppService.cancelStudentAppointment(cancelStudentAppointmentCmd);
    }
}
