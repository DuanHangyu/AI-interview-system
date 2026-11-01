package system.assessment.defense.infrastructure.repository.dao.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;
import system.assessment.defense.infrastructure.repository.dao.mapper.StudentAssessmentQuestionAnswerMapper;
import system.assessment.defense.infrastructure.repository.dao.po.StudentAssessmentQuestionAnswerPO;
import system.assessment.defense.infrastructure.repository.dao.service.StudentAssessmentQuestionAnswerService;

import java.util.List;

/**
 * @USER taoHouChao
 * @DATE 20:05 2025/8/15
 */
@Repository
public class StudentAssessmentQuestionAnswerServiceImpl extends ServiceImpl<StudentAssessmentQuestionAnswerMapper, StudentAssessmentQuestionAnswerPO>
        implements StudentAssessmentQuestionAnswerService {
    @Override
    public List<StudentAssessmentQuestionAnswerPO> listByAssessmentIdAndStudentId(Integer assessmentId, Integer studentId) {
        return this.list(Wrappers.lambdaQuery(StudentAssessmentQuestionAnswerPO.class)
                .eq(StudentAssessmentQuestionAnswerPO::getAssessmentId, assessmentId)
                .eq(StudentAssessmentQuestionAnswerPO::getStudentId, studentId));
    }

    @Override
    public void clearQuestionAnswer(Integer studentId, int assessmentId) {
        // 先查主键再按主键删除：按 (assessment_id, student_id) 直接 DELETE 会在二级索引上加间隙锁，
        // 同一场考核的学生同时开始面试时会互相死锁
        List<Integer> ids = this.list(Wrappers.lambdaQuery(StudentAssessmentQuestionAnswerPO.class)
                        .select(StudentAssessmentQuestionAnswerPO::getId)
                        .eq(StudentAssessmentQuestionAnswerPO::getStudentId, studentId)
                        .eq(StudentAssessmentQuestionAnswerPO::getAssessmentId, assessmentId))
                .stream().map(StudentAssessmentQuestionAnswerPO::getId).toList();
        if (!ids.isEmpty()) {
            this.removeByIds(ids);
        }
    }
}
