package system.assessment.defense.infrastructure.repository.dao.service;

import com.baomidou.mybatisplus.extension.service.IService;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentQuestionPO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface AssessmentQuestionService extends IService<AssessmentQuestionPO> {

    /**
     * 考核的题库，按 sort 排序
     */
    List<AssessmentQuestionPO> listByAssessmentId(Integer assessmentId);

    Map<Integer, List<AssessmentQuestionPO>> mapByAssessmentIds(Collection<Integer> assessmentIds);
}
