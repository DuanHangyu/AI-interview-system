package system.assessment.defense.infrastructure.config;

import cn.dev33.satoken.exception.NotLoginException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import system.assessment.defense.infrastructure.exception.BusinessException;

/**
 * @Author taoHouChao
 * @Date 12:27 2025/6/7
 */
@RestControllerAdvice
@Slf4j
public class GlobalException {

    private static final String DATABASE_CONNECTION_ERROR =
            "数据库连接失败，请检查本地数据库隧道或 DB_JDBC_URL 配置";

    @ExceptionHandler(DuplicateKeyException.class)
    public BaseResult handleException(DuplicateKeyException e){
        log.error("系统异常", e);
        return BaseResult.error(600, "存在相同的数据");
    }

    @ExceptionHandler(BusinessException.class)
    public BaseResult<Void> handleException(BusinessException e){
        log.error("e", e);
        return BaseResult.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    public BaseResult<Void> handleException(NotLoginException e){
        log.error("e", e);
        return BaseResult.error(401, e.getMessage());
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public BaseResult<Void> handleDataAccessResourceFailure(DataAccessResourceFailureException e){
        log.error("数据库连接异常", e);
        return BaseResult.error(500, DATABASE_CONNECTION_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public BaseResult<Void> handleException(Exception e){
        log.error("e", e);
        if (hasCause(e, DataAccessResourceFailureException.class)) {
            return BaseResult.error(500, DATABASE_CONNECTION_ERROR);
        }
        return BaseResult.error(500, "系统异常");
    }

    private boolean hasCause(Throwable e, Class<? extends Throwable> causeType) {
        Throwable current = e;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
