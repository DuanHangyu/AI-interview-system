package system.assessment.defense.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import system.assessment.defense.application.dto.ProjectTypeCreateCmd;
import system.assessment.defense.application.dto.ProjectTypeDTO;
import system.assessment.defense.application.dto.ProjectTypeDeleteCmd;
import system.assessment.defense.infrastructure.repository.dao.po.ProjectTypePO;
import system.assessment.defense.infrastructure.repository.dao.service.ProjectTypeService;

import java.util.List;
import java.util.UUID;

/**
 * @USER taoHouChao
 * @DATE 15:27 2025/8/18
 */
@Service
@RequiredArgsConstructor
public class ProjectTypeAppService {

    private final ProjectTypeService projectTypeService;

    public List<ProjectTypeDTO> list() {
        return projectTypeService.list().stream()
                .map(item -> new ProjectTypeDTO(item.getId(), item.getProjectTypeKey(), item.getProjectType()))
                .toList();
    }

    public Boolean create(ProjectTypeCreateCmd createCmd) {
        List<ProjectTypePO> types = createCmd.getProjectTypes().stream()
                .map(item -> ProjectTypePO.builder()
                        .projectTypeKey(UUID.randomUUID().toString())
                        .projectType(item)
                        .build()).toList();
        return projectTypeService.saveBatch(types);
    }

    public Boolean delete(ProjectTypeDeleteCmd deleteCmd) {
        return projectTypeService.removeById(deleteCmd.getId());
    }
}
