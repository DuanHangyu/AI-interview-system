package system.assessment.defense.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "AI 生成题库草稿")
public class QuestionDraftCmd {

    private String theme;

    private String assessmentRequirements;

    private String assessmentCriteria;

    @Schema(description = "生成题目数量")
    private Integer count;

    private List<FileDTO> assessmentFiles;
}
