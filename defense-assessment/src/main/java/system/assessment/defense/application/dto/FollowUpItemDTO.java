package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 题库中一道题的一个预制追问。题库里以 JSON 数组存储（audioKey 只在服务端使用，不返回给前端）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "预制追问")
public class FollowUpItemDTO {

    @Schema(description = "追问内容（会播报给学生）")
    private String content;

    @Schema(description = "考察意图，仅供 AI 评分参考，不展示给学生")
    private String intent;

    @Schema(description = "播报音频存储 key（仅服务端）", hidden = true)
    private String audioKey;

    @Schema(description = "播报音频是否已生成（只读）")
    private Boolean audioReady;
}
