package system.assessment.defense.application.dto.backed;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * @USER taoHouChao
 * @DATE 22:36 2025/9/26
 */
@Data
@Schema(description = "学生记录查询参数")
public class StudentRecordExcelQuery {

    @Schema(description = "考核主题")
    private String theme;

    @Schema(description = "考核记录ID列表")
    private List<Integer> recordIds;
}
