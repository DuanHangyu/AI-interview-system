package system.assessment.defense.interfaces.rest.backend;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import system.assessment.defense.application.dto.*;
import system.assessment.defense.application.dto.backed.SchoolClassStudentDTO;
import system.assessment.defense.application.service.StudentAppService;
import system.assessment.defense.infrastructure.common.ExcelUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @USER taoHouChao
 * @DATE 21:03 2025/8/7
 */
@RestController
@RequestMapping("/backend/student")
@Tag(name = "学生管理", description = "学生管理")
@Validated
public class StudentBackendController {

    @Resource
    private StudentAppService studentAppService;

    @PostMapping("/page-list")
    @Operation(summary = "分页查询学生")
    public IPage<StudentDTO> pageList(@RequestBody StudentPageQuery query){
        return studentAppService.pageList(query);
    }

    @PostMapping("/create")
    @Operation(summary = "创建学生")
    public Boolean createStudent(@RequestBody StudentCreateCmd createCmd){
        return studentAppService.createStudent(createCmd);
    }

    @PostMapping("/modify")
    @Operation(summary = "修改学生")
    public Boolean modifyStudent(@RequestBody StudentModifyCmd modifyCmd){
        return studentAppService.modifyStudent(modifyCmd);
    }

    @PostMapping("/delete")
    @Operation(summary = "删除学生")
    public Boolean deleteStudent(@RequestBody StudentRemoveCmd removeCmd){
        return studentAppService.deleteStudent(removeCmd);
    }

    @PostMapping("/export-template")
    @Operation(summary = "下载模板")
    public void exportTemplate(){
        List<StudentExcelDTO> studentExcelList = new ArrayList<>(1);
        studentExcelList.add(StudentExcelDTO.empty());
        ExcelUtils.exportTemplate(studentExcelList, "学生导入模版");
    }

    @PostMapping("/import")
    @Operation(summary = "导入学生")
    public Boolean importStudent(MultipartFile file){
        List<StudentExcelDTO> students = ExcelUtils.readExcel(file, StudentExcelDTO.class);
        if (CollectionUtils.isEmpty(students)) {
            return true;
        }
        return studentAppService.importStudent(students);
    }

    @PostMapping("/export")
    @Operation(summary = "导出学生")
    public void exportStudent(@RequestBody StudentExportCmd exportCmd) throws IOException {
        List<StudentExcelDTO> studentExcels = studentAppService.exportStudent(exportCmd);
        ExcelUtils.exportExcel(studentExcels);
    }

    @GetMapping("/executor")
    @Operation(summary = "批量执行")
    public void executor() {
        studentAppService.batchAnalysisUserInfo();
    }

    @GetMapping("/all")
    @Operation(summary = "获取所有学生")
    public List<StudentDTO> all() {
        return studentAppService.allStudent();
    }

    @GetMapping("/school-class-student")
    @Operation(summary = "获取班级学生")
    public List<SchoolClassStudentDTO> schoolClassStudentList() {
        List<StudentDTO> students = studentAppService.allStudent();
        Map<String, List<StudentDTO>> schoolClassMap = students.stream()
                .collect(Collectors.groupingBy(StudentDTO::getSchoolClass));
        return schoolClassMap.entrySet().stream()
                .map(item -> {
                    SchoolClassStudentDTO schoolClassStudentDTO = new SchoolClassStudentDTO();
                    schoolClassStudentDTO.setSchoolClass(item.getKey());
                    schoolClassStudentDTO.setStudents(item.getValue());
                    return schoolClassStudentDTO;
                }).toList();
    }
}
