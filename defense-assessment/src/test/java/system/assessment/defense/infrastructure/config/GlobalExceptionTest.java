package system.assessment.defense.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionTest {

    @Test
    void handleDataAccessResourceFailureReturnsActionableMessage() {
        GlobalException globalException = new GlobalException();

        BaseResult<Void> result = globalException.handleDataAccessResourceFailure(
                new DataAccessResourceFailureException("connection refused"));

        assertEquals(500, result.getCode());
        assertEquals("数据库连接失败，请检查本地数据库隧道或 DB_JDBC_URL 配置", result.getMessage());
    }

    @Test
    void handleExceptionUnwrapsNestedDataAccessResourceFailure() {
        GlobalException globalException = new GlobalException();

        BaseResult<Void> result = globalException.handleException(new RuntimeException(
                "wrapped",
                new DataAccessResourceFailureException("connection refused")));

        assertEquals(500, result.getCode());
        assertEquals("数据库连接失败，请检查本地数据库隧道或 DB_JDBC_URL 配置", result.getMessage());
    }
}
