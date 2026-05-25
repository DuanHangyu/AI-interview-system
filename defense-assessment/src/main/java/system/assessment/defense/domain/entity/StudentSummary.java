package system.assessment.defense.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentStudentRelationPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentAppointmentPO;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentRecordPO;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @USER taoHouChao
 * @DATE 18:24 2025/9/26
 */
@Data
@Schema(description = "用户信息")
public class StudentSummary {

    private Integer id;

    private List<StudentAssessmentRecordPO> records;

    private List<AssessmentStudentRelationPO> relations;

    private List<StudentAssessmentAppointmentPO> appointments;

    /**
     * 获取待处理的评估ID列表
     * 预约中，考核中，无需预约，重考
     * @param noNeedAppointAssessmentFunction 用于处理无需预约评估ID的函数，接收无需预约的评估ID列表，返回处理后的ID列表
     * @return 待处理的评估ID列表，包含所有需要处理的评估项目ID
     */
    public List<Integer> todoAssessmentIds(Function<List<Integer> , List<Integer>> noNeedAppointAssessmentFunction) {

        // 获取状态为0或1的预约中的评估ID列表
        Set<Integer> todoAppointAssessmentIds = appointments.stream()
                .filter(item -> item.getState() == 0 || item.getState() == 1)
                .map(StudentAssessmentAppointmentPO::getAssessmentId)
                .collect(Collectors.toSet());
        // 加上重考的
        todoAppointAssessmentIds.addAll(records.stream()
                .filter(item -> Objects.equals(item.getState(), 3))
                .map(StudentAssessmentRecordPO::getAssessmentId)
                .toList());

        List<Integer> relationAssessmentIds = new ArrayList<>(relations.stream().map(AssessmentStudentRelationPO::getAssessmentId).toList());
        // 已经预约的剔除
        relationAssessmentIds.removeIf(todoAppointAssessmentIds::contains);
        // 已经考核完成或在分析中的剔除
        Set<Integer> recordAssessmentIds = records.stream()
                .filter(item -> Objects.equals(item.getState(), 1) || Objects.equals(item.getState(), 2))
                .map(StudentAssessmentRecordPO::getAssessmentId).collect(Collectors.toSet());
        relationAssessmentIds.removeIf(recordAssessmentIds::contains);
        if (CollectionUtils.isNotEmpty(relationAssessmentIds)) {
            // 获取无需预约的加入
            List<Integer> noNeedAppointAssessmentIds = noNeedAppointAssessmentFunction.apply(relationAssessmentIds);
            todoAppointAssessmentIds.addAll(noNeedAppointAssessmentIds);
        }
        return new ArrayList<>(todoAppointAssessmentIds);
    }

    public List<Integer> toAppointAssessmentIds(Function<List<Integer> , List<Integer>> noNeedAppointAssessFunction,
                                                Function<List<Integer> , List<Integer>> timeExpireAppointmentFunction) {
        List<Integer> relationAssessmentIds = new ArrayList<>(relations.stream()
                .map(AssessmentStudentRelationPO::getAssessmentId)
                .toList());
        // 已经预约的剔除
        Set<Integer> appointAssessmentIds = appointments.stream()
                .map(StudentAssessmentAppointmentPO::getAssessmentId)
                .collect(Collectors.toSet());
        relationAssessmentIds.removeIf(appointAssessmentIds::contains);
        // 不需要预约的剔除
        List<Integer> noNeedAppointAssessmentIds = noNeedAppointAssessFunction.apply(relationAssessmentIds);
        relationAssessmentIds.removeIf(noNeedAppointAssessmentIds::contains);

        // 考核通过的剔除
        Set<Integer> recordAssessmentIds = records.stream()
                .map(StudentAssessmentRecordPO::getAssessmentId)
                .collect(Collectors.toSet());
        relationAssessmentIds.removeIf(recordAssessmentIds::contains);

        // 过期的预约剔除
        if (CollectionUtils.isNotEmpty(relationAssessmentIds)) {
            List<Integer> timeExpireAppointmentIds = timeExpireAppointmentFunction.apply(relationAssessmentIds);
            relationAssessmentIds.removeIf(timeExpireAppointmentIds::contains);
        }
        return relationAssessmentIds.stream().distinct().toList();
    }

    /**
     * 已完成的包含考核完成的，预约未考核的，解除惩罚的，绑定了该考核，但是没预约，没考核的
     * @param timeExpireAppointmentFunction 查询过期的考核id集合
     * @return 可显示的考核ID列表
     */
    public List<Integer> doneAssessmentIds(Function<List<Integer> , List<Integer>> timeExpireAppointmentFunction) {
        // 定义状态常量，提高可读性
        final int ASSESSMENT_IN_PROCESSING = 0;          // 考核未开始状态
        final int ASSESSMENT_DONE = 1;                // 考核完成状态
        final int ASSESSMENT_ANALYZING = 2;           // 分析中状态
        final int APPOINTMENT_CANCELLED = 3;          // 预约未考状态
        final int APPOINTMENT_EXPIRED = 4;            // 可重新预约状态
        final int APPOINTMENT_CONFIRMED = 0;          // 已确认预约状态

        // 已经考核的
        Set<Integer> doneAssessmentIds = records.stream()
                .filter(item -> Objects.equals(item.getState(), ASSESSMENT_DONE))
                .map(StudentAssessmentRecordPO::getAssessmentId).collect(Collectors.toSet());

        // 预约取消或过期的
        Set<Integer> appointButNotAssessIds = appointments.stream()
                .filter(item -> item.getState() == APPOINTMENT_CANCELLED || item.getState() == APPOINTMENT_EXPIRED)
                .map(StudentAssessmentAppointmentPO::getAssessmentId)
                .collect(Collectors.toSet());
        doneAssessmentIds.addAll(appointButNotAssessIds);

        List<Integer> relationAssessmentIds = new ArrayList<>(relations.stream()
                .map(AssessmentStudentRelationPO::getAssessmentId)
                .toList());
        relationAssessmentIds.removeIf(doneAssessmentIds::contains);

        // 预约了但还没考核的
        Set<Integer> appointNotAssessIds = appointments.stream()
                .filter(item -> item.getState() == APPOINTMENT_CONFIRMED)
                .map(StudentAssessmentAppointmentPO::getAssessmentId)
                .collect(Collectors.toSet());
        relationAssessmentIds.removeIf(appointNotAssessIds::contains);

        // 分析中的，考核未开始，考核重考
        Set<Integer> analysisAssessIds = records.stream()
                .filter(item -> Objects.equals(item.getState(), ASSESSMENT_ANALYZING) || Objects.equals(item.getState(), ASSESSMENT_IN_PROCESSING) || Objects.equals(item.getState(), 3))
                .map(StudentAssessmentRecordPO::getAssessmentId)
                .collect(Collectors.toSet());
        relationAssessmentIds.removeIf(analysisAssessIds::contains);

        if (CollectionUtils.isNotEmpty(relationAssessmentIds)) {
            // 加入过期未预约的
            List<Integer> timeExpireAppointIds = timeExpireAppointmentFunction.apply(relationAssessmentIds);
            doneAssessmentIds.addAll(timeExpireAppointIds);
        }

        return new ArrayList<>(doneAssessmentIds);
    }
}
