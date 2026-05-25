package system.assessment.defense.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import system.assessment.defense.application.dto.ProjectTypeCreateCmd;
import system.assessment.defense.application.dto.ProjectTypeDTO;
import system.assessment.defense.application.dto.ProjectTypeDeleteCmd;
import system.assessment.defense.application.service.ProjectTypeAppService;

import java.util.List;

/**
 * @USER taoHouChao
 * @DATE 15:24 2025/8/18
 */
@RestController
@RequestMapping("/project-type")
@Tag(name = "项目类型管理", description = "项目类型管理")
public class ProjectTypeController {

    @Resource
    private ProjectTypeAppService projectTypeAppService;

    @GetMapping("/list")
    @Operation(summary = "获取项目类型列表")
    public List<ProjectTypeDTO> list(){
        return projectTypeAppService.list();
    }

    @PostMapping("/create")
    @Operation(summary = "创建项目类型")
    public Boolean create(@RequestBody ProjectTypeCreateCmd createCmd){
        return projectTypeAppService.create(createCmd);
    }

    @PostMapping("/delete")
    @Operation(summary = "删除项目类型")
    public Boolean delete(@RequestBody ProjectTypeDeleteCmd deleteCmd){
        return projectTypeAppService.delete(deleteCmd);
    }
}
