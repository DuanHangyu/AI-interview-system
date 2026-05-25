package system.assessment.defense.application.dto.backed;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentAppointmentSettingPO;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @USER taoHouChao
 * @DATE 20:38 2025/9/23
 */
@Data
@Schema(description = "考核预约命令")
public class AppointmentSettingCreateCmd {

    @Schema(description = "考核设置id")
    private Integer assessmentId;

    @Schema(description = "可以预约时间段")
    private List<TimePeriod> timePeriods;

    @Schema(description = "考核失败是否惩罚")
    private Boolean assessmentFailPunish;

    @Schema(description = "可重新预约时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime rescheduleAppointTime;

    public List<AssessmentAppointmentSettingPO> toAppointmentSettingPOList() {
        return timePeriods.stream().map(timePeriod -> AssessmentAppointmentSettingPO.builder()
                .id(timePeriod.getId())
                .assessmentId(assessmentId)
                .timePeriod(timePeriod.getTimePeriod())
                .participantLimit(timePeriod.getParticipantLimit())
                .location(timePeriod.getLocation())
                .build()).collect(Collectors.toList());
    }

    public LocalDateTime lastTimePeriod() {
        return timePeriods.stream().max(Comparator.comparing(TimePeriod::getTimePeriod)).map(TimePeriod::getTimePeriod).orElse(null);
    }

    @Data
    @Schema(description = "考核时间段")
    public static class TimePeriod{

        @Schema(description = "时间段id")
        private Integer id;

        @Schema(description = "时间段")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private LocalDateTime timePeriod;

        @Schema(description = "该时间段内的限制人数")
        private Integer participantLimit;

        @Schema(description = "地点")
        private String location;
    }
}
