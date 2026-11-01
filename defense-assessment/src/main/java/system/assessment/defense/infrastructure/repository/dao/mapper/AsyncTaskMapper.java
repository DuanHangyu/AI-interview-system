package system.assessment.defense.infrastructure.repository.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import system.assessment.defense.infrastructure.repository.dao.po.AsyncTaskPO;

import java.util.List;

/**
 * 所有时间判断都使用数据库的 NOW()，避免多台应用服务器之间的时钟偏差。
 */
public interface AsyncTaskMapper extends BaseMapper<AsyncTaskPO> {

    /**
     * 入队；同一 (task_type, biz_id) 已存在时重置为待执行。正在执行中的任务保持不变，避免并发重复执行。
     * 注意 MySQL 按书写顺序求值赋值表达式，status 必须放在最后再改。
     */
    @Insert("""
            INSERT INTO async_task (task_type, biz_id, status, attempts, max_attempts, next_run_time, create_time, update_time)
            VALUES (#{type}, #{bizId}, 0, 0, #{maxAttempts}, DATE_ADD(NOW(), INTERVAL #{delaySeconds} SECOND), NOW(), NOW())
            ON DUPLICATE KEY UPDATE
              attempts = IF(status = 1, attempts, 0),
              next_run_time = IF(status = 1, next_run_time, VALUES(next_run_time)),
              last_error = IF(status = 1, last_error, NULL),
              max_attempts = VALUES(max_attempts),
              update_time = NOW(),
              status = IF(status = 1, 1, 0)
            """)
    int upsert(@Param("type") String type, @Param("bizId") Integer bizId,
               @Param("maxAttempts") int maxAttempts, @Param("delaySeconds") int delaySeconds);

    @Select("""
            SELECT id FROM async_task
            WHERE task_type = #{type}
              AND ((status = 0 AND next_run_time <= NOW()) OR (status = 1 AND locked_until < NOW()))
            ORDER BY next_run_time
            LIMIT #{limit}
            """)
    List<Long> findDue(@Param("type") String type, @Param("limit") int limit);

    /**
     * 乐观抢占：只有一个实例能把任务从“可执行”改为“执行中”。
     */
    @Update("""
            UPDATE async_task
            SET status = 1, attempts = attempts + 1, owner = #{owner},
                locked_until = DATE_ADD(NOW(), INTERVAL #{leaseSeconds} SECOND), update_time = NOW()
            WHERE id = #{id}
              AND ((status = 0 AND next_run_time <= NOW()) OR (status = 1 AND locked_until < NOW()))
            """)
    int claim(@Param("id") Long id, @Param("owner") String owner, @Param("leaseSeconds") int leaseSeconds);

    @Update("""
            UPDATE async_task SET status = 2, locked_until = NULL, last_error = NULL, update_time = NOW()
            WHERE id = #{id} AND owner = #{owner} AND status = 1
            """)
    int complete(@Param("id") Long id, @Param("owner") String owner);

    /**
     * 失败：未达最大次数则按退避时间重新排队，否则标记为已放弃。
     */
    @Update("""
            UPDATE async_task
            SET last_error = #{error}, locked_until = NULL, update_time = NOW(),
                next_run_time = DATE_ADD(NOW(), INTERVAL #{retryDelaySeconds} SECOND),
                status = IF(attempts >= max_attempts, 3, 0)
            WHERE id = #{id} AND owner = #{owner} AND status = 1
            """)
    int fail(@Param("id") Long id, @Param("owner") String owner,
             @Param("error") String error, @Param("retryDelaySeconds") int retryDelaySeconds);

    /**
     * 依赖未就绪，延后执行且不消耗重试次数。
     */
    @Update("""
            UPDATE async_task
            SET status = 0, attempts = GREATEST(attempts - 1, 0), locked_until = NULL, update_time = NOW(),
                next_run_time = DATE_ADD(NOW(), INTERVAL #{delaySeconds} SECOND)
            WHERE id = #{id} AND owner = #{owner} AND status = 1
            """)
    int defer(@Param("id") Long id, @Param("owner") String owner, @Param("delaySeconds") int delaySeconds);

    @Select("SELECT status FROM async_task WHERE task_type = #{type} AND biz_id = #{bizId}")
    Integer findStatus(@Param("type") String type, @Param("bizId") Integer bizId);
}
