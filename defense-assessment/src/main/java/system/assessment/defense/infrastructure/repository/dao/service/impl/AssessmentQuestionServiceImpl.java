package system.assessment.defense.infrastructure.repository.dao.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Repository;
import system.assessment.defense.infrastructure.repository.dao.mapper.AssessmentQuestionMapper;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentQuestionPO;
import system.assessment.defense.infrastructure.repository.dao.service.AssessmentQuestionService;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class AssessmentQuestionServiceImpl extends ServiceImpl<AssessmentQuestionMapper, AssessmentQuestionPO>
        implements AssessmentQuestionService {

    @Override
    public List<AssessmentQuestionPO> listByAssessmentId(Integer assessmentId) {
        return this.list(Wrappers.lambdaQuery(AssessmentQuestionPO.class)
                .eq(AssessmentQuestionPO::getAssessmentId, assessmentId)
                .orderByAsc(AssessmentQuestionPO::getSort)
                .orderByAsc(AssessmentQuestionPO::getId));
    }

    @Override
    public Map<Integer, List<AssessmentQuestionPO>> mapByAssessmentIds(Collection<Integer> assessmentIds) {
        if (CollectionUtils.isEmpty(assessmentIds)) {
            return Collections.emptyMap();
        }
        return this.list(Wrappers.lambdaQuery(AssessmentQuestionPO.class)
                        .in(AssessmentQuestionPO::getAssessmentId, assessmentIds)
                        .orderByAsc(AssessmentQuestionPO::getSort)
                        .orderByAsc(AssessmentQuestionPO::getId))
                .stream()
                .collect(Collectors.groupingBy(AssessmentQuestionPO::getAssessmentId));
    }
}
