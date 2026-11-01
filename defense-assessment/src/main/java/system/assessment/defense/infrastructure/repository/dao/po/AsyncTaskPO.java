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
@TableName("async_task")
@Schema(description = "持久化的后台任务（语音转写、评分、语音合成），支持多实例抢占、失败退避重试")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AsyncTaskPO {

    public static final int PENDING = 0;
    public static final int RUNNING = 1;
    public static final int SUCCESS = 2;
    public static final int FAILED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskType;

    private Integer bizId;

    @Schema(description = "0待执行 1执行中 2成功 3失败(已放弃)")
    private Integer status;

    private Integer attempts;

    private Integer maxAttempts;

    private LocalDateTime nextRunTime;

    @Schema(description = "执行中任务的租约到期时间，过期视为执行者已崩溃，可被重新抢占")
    private LocalDateTime lockedUntil;

    private String owner;

    private String lastError;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
