package system.assessment.defense.infrastructure.repository.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @USER taoHouChao
 * @DATE 15:28 2025/8/18
 */
@Data
@TableName("project_type")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProjectTypePO {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "项目类型key")
    private String projectTypeKey;

    @Schema(description = "项目类型")
    private String projectType;
}
