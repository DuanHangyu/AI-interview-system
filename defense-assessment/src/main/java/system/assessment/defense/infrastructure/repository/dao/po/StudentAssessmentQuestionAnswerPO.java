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

/**
 * @USER taoHouChao
 * @DATE 19:59 2025/8/15
 */
@Data
@TableName("student_assessment_question_answer")
@Schema(description = "学生考核问题答案")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StudentAssessmentQuestionAnswerPO {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer assessmentId;

    private Integer studentId;

    /**
     * 抽中的题库题目 id
     */
    private Integer questionId;

    /**
     * 追问所属的主问题行 id；主问题为空。追问与主问题一样单独一行，便于逐条上传、转写
     */
    private Integer parentId;

    /**
     * 追问序号，从 1 开始；(parent_id, follow_index) 唯一
     */
    private Integer followIndex;

    /**
     * 追问考察意图快照，仅供评分参考
     */
    private String followIntent;

    /**
     * 本场面试中的题序，从 1 开始；(student_id, assessment_id, sort_index) 唯一，保证重复抽题请求幂等
     */
    private Integer sortIndex;

    private String dimension;

    /**
     * 抽题时的参考答案快照，教师之后修改题库不影响已完成的面试评分
     */
    private String referenceAnswer;

    /**
     * 抽题时的评分标准快照（QuestionRubric JSON）
     */
    private String rubric;

    private String question;

    private String answer;

    private String answerVoice;

    private LocalDateTime createTime;

    /**
     * 由数据库维护（ON UPDATE），考场监控据此显示学生最后一次作答的时间
     */
    private LocalDateTime updateTime;
}
