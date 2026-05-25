package system.assessment.defense.interfaces.rest.backend;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import system.assessment.defense.application.dto.*;
import system.assessment.defense.application.dto.backed.TeacherModifyPasswordCmd;
import system.assessment.defense.application.service.TeacherAppService;

import java.util.List;

/**
 * @USER taoHouChao
 * @DATE 06:47 2025/8/14
 */
@RestController
@RequestMapping("/backend/teacher")
@Tag(name = "教师管理", description = "教师管理")
public class TeacherBackendController {

    @Resource
    private TeacherAppService teacherAppService;

    @PostMapping("/page-list")
    @Operation(summary = "分页查询教师")
    public IPage<TeacherDTO> pageList(@RequestBody TeacherPageQuery query){
        return teacherAppService.pageList(query);
    }

    @PostMapping("/create")
    @Operation(summary = "创建教师")
    public Boolean createStudent(@RequestBody TeacherCreateCmd createCmd){
        return teacherAppService.createStudent(createCmd);
    }

    @PostMapping("/modify")
    @Operation(summary = "修改教师")
    public Boolean modifyStudent(@RequestBody TeacherModifyCmd modifyCmd){
        return teacherAppService.modifyStudent(modifyCmd);
    }

    @PostMapping("/delete")
    @Operation(summary = "删除教师")
    public Boolean deleteStudent(@RequestBody TeacherRemoveCmd removeCmd){
        return teacherAppService.deleteStudent(removeCmd);
    }

    @GetMapping("/all-teacher")
    @Operation(summary = "获取所有教师")
    public List<TeacherDTO> allTeacher(){
        return teacherAppService.allTeacher();
    }

    @PostMapping("/modify-password")
    @Operation(summary = "修改密码")
    public Boolean modifyPassword(@RequestBody TeacherModifyPasswordCmd modifyPasswordCmd){
        return teacherAppService.modifyPassword(modifyPasswordCmd);
    }
}
