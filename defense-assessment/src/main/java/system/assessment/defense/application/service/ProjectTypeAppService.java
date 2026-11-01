package system.assessment.defense.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import system.assessment.defense.application.dto.ProjectTypeDTO;
import system.assessment.defense.infrastructure.repository.dao.service.ProjectTypeService;

import java.util.List;

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

}
