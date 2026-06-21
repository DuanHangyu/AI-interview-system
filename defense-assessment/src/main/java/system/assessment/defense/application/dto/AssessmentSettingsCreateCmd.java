package system.assessment.defense.application.dto;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import system.assessment.defense.application.dto.backed.SchoolClassStudentSaveCmd;
import system.assessment.defense.infrastructure.emuns.ErrorCodeEnums;
import system.assessment.defense.infrastructure.exception.BusinessException;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentSettingPO;

import java.util.List;
import java.util.Objects;

/**
 * @USER taoHouChao
 * @DATE 19:51 2025/8/7
 */
@Data
@Schema(description = "考核设置创建命令")
public class AssessmentSettingsCreateCmd {

    @Schema(description = "考核主题")
    @NotEmpty(message = "考核主题不能为空")
    private String theme;

    @Schema(description = "考核标准")
    @NotEmpty(message = "考核标准不能为空")
    private String assessmentCriteria;

    @Schema(description = "考核时长")
    @NotNull(message = "考核时长不能为空")
    private Integer duration;

    @Schema(description = "考核题目数量")
    private Integer questionCount;

    @Schema(description = "考核答题时间")
    @NotNull(message = "考核答题时间不能为空")
    private Integer answerTime;

    @Schema(description = "是否需要追问")
    @NotNull(message = "是否需要追问不能为空")
    private Boolean followUp;

    @Schema(description = "追问提示词")
    private String followUpPrompt;

    @Schema(description = "追问标准")
    private String followUpStandards;

    @Schema(description = "考核文件")
    private List<FileDTO> assessmentFiles;

    @Schema(description = "总分")
    private Integer totalScore;

    @Schema(description = "通过分数")
    private Integer passScore;

    @Schema(description = "考核要求")
    private String assessmentRequirements;

    @Schema(description = "是否答辩")
    private Boolean defense;

    @Schema(description = "是否提问")
    private Boolean question;

    @Schema(description = "是否显示结果")
    private Boolean showResult;

    @Schema(description = "是否需要预约")
    private Boolean needAppoint;

    @Schema(description = "邀请教师id集合")
    private List<Integer> inviteTeachers;

    @Schema(description = "班级名称集合")
    private List<SchoolClassStudentSaveCmd> schoolClassList;

    @Schema(description = "学生id集合")
    private List<Integer> studentIds;


    @Schema(description = "数字人id")
    private Integer digitalHumanId;

    public AssessmentSettingPO toPO(){
        return AssessmentSettingPO.builder()
                .theme(theme)
                .assessmentRequirements(assessmentRequirements)
                .assessmentCriteria(assessmentCriteria)
                .duration(duration)
                .questionCount(questionCount)
                .answerTime(answerTime)
                .followUp(followUp)
                .followUpPrompt(followUpPrompt)
                .followUpStandards(followUpStandards)
                .assessmentFiles(assessmentFiles == null ? "" : JSONUtil.toJsonStr(assessmentFiles))
                .totalScore(totalScore)
                .passScore(passScore)
                .defense(defense)
                .question(question)
                .showResult(showResult)
                .needAppoint(needAppoint)
                .digitalHumanId(digitalHumanId)
                .createId(StpUtil.getLoginIdAsInt())
                .build();
    }

    public void check() {
    }
}
