package system.assessment.defense.infrastructure.repository.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@TableName("assessment_question")
@Schema(description = "考核题库中的一道预制题目")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AssessmentQuestionPO {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer assessmentId;

    @Schema(description = "考核维度，用于均衡抽题；为空视为同一维度")
    private String dimension;

    @Schema(description = "题目内容（支持 Markdown）")
    private String content;

    @Schema(description = "参考答案 / 评分要点，仅供 AI 评分使用，不展示给学生")
    private String referenceAnswer;

    @Schema(description = "预制追问，为空表示该题不追问")
    private String followUpQuestion;

    private Integer sort;

    @Schema(description = "题目播报音频的存储 key，内容变更后清空并重新生成")
    private String audioKey;

    @Schema(description = "追问播报音频的存储 key")
    private String followUpAudioKey;

    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
