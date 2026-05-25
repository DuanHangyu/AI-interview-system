package system.assessment.defense.infrastructure.repository.dao.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;
import system.assessment.defense.infrastructure.repository.dao.mapper.ProjectTypeMapper;
import system.assessment.defense.infrastructure.repository.dao.po.ProjectTypePO;
import system.assessment.defense.infrastructure.repository.dao.service.ProjectTypeService;

/**
 * @USER taoHouChao
 * @DATE 15:29 2025/8/18
 */
@Repository
public class ProjectTypeServiceIml extends ServiceImpl<ProjectTypeMapper, ProjectTypePO> implements ProjectTypeService {
}
