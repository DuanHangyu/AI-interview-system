package system.assessment.defense.application.dto.backed;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import system.assessment.defense.application.dto.FileDTO;
import system.assessment.defense.infrastructure.repository.dao.po.DigitalHumanPO;

import java.util.function.Function;

/**
 * @USER taoHouChao
 * @DATE 23:02 2025/10/2
 */
@Data
@Schema(description = "数字人信息")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DigitalHumanDTO {

    @Schema(description = "id")
    @NotNull(message = "id不能为空")
    private Integer id;

    @Schema(description = "名称")
    @NotEmpty(message = "名称不能为空")
    private String name;

    @Schema(description = "视频")
    @NotEmpty(message = "视频不能为空")
    private FileDTO video;

    @Schema(description = "口型")
    @NotEmpty(message = "口型不能为空")
    private FileDTO lipShape;

    @Schema(description = "是否默认")
    @NotNull(message = "是否默认不能为空")
    private Boolean defaultFlag;

    @Schema(description = "是否已使用")
    @NotNull(message = "是否已使用不能为空")
    private Boolean useFlag;

    public static DigitalHumanDTO of(DigitalHumanPO item, Function<String, FileDTO> convertToFileFunction){
        return DigitalHumanDTO.builder()
                .id(item.getId())
                .name(item.getName())
                .video(convertToFileFunction.apply(item.getVideo()))
                .lipShape(convertToFileFunction.apply(item.getLipShape()))
                .defaultFlag(item.getDefaultFlag())
                .build();
    }
}
