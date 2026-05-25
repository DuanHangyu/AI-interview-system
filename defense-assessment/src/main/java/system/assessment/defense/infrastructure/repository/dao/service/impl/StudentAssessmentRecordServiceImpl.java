package system.assessment.defense.infrastructure.repository.dao.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;
import system.assessment.defense.infrastructure.repository.dao.mapper.StudentAssessmentRecordMapper;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentRecordPO;
import system.assessment.defense.infrastructure.repository.dao.service.StudentAssessmentRecordService;

import java.util.List;
import java.util.Optional;

/**
 * @USER taoHouChao
 * @DATE 20:39 2025/8/14
 */
@Repository
public class StudentAssessmentRecordServiceImpl extends ServiceImpl<StudentAssessmentRecordMapper, StudentAssessmentRecordPO>
        implements StudentAssessmentRecordService {
    @Override
    public List<StudentAssessmentRecordPO> findByStudentId(Integer studentId) {
        return this.list(Wrappers.lambdaQuery(StudentAssessmentRecordPO.class)
                .eq(StudentAssessmentRecordPO::getStudentId, studentId));
    }

    @Override
    public Optional<StudentAssessmentRecordPO> findByStudentIdAndAssessmentId(Integer studentId, Integer assessmentId) {
        return Optional.ofNullable(this.getOne(Wrappers.lambdaQuery(StudentAssessmentRecordPO.class)
                .eq(StudentAssessmentRecordPO::getStudentId, studentId)
                .eq(StudentAssessmentRecordPO::getAssessmentId, assessmentId)));
    }

    @Override
    public List<StudentAssessmentRecordPO> findByStudentIdAndAssessmentIds(Integer studentId, List<Integer> assessmentIds) {
        return this.list(Wrappers.lambdaQuery(StudentAssessmentRecordPO.class)
                .eq(StudentAssessmentRecordPO::getStudentId, studentId)
                .in(StudentAssessmentRecordPO::getAssessmentId, assessmentIds));
    }

    @Override
    public List<StudentAssessmentRecordPO> findbyStudentIds(List<Integer> studentIds) {
        return this.list(Wrappers.lambdaQuery(StudentAssessmentRecordPO.class)
                .in(StudentAssessmentRecordPO::getStudentId, studentIds));
    }
}
