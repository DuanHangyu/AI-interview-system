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
 * @DATE 22:31 2025/10/2
 */
@Data
@Schema(description = "数字人")
@TableName("digital_human")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DigitalHumanPO {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "视频")
    private String video;

    @Schema(description = "口型")
    private String lipShape;

    @Schema(description = "是否默认")
    private Boolean defaultFlag;
}
