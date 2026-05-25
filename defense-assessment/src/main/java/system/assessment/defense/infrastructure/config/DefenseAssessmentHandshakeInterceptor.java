package system.assessment.defense.infrastructure.config;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;

/**
 * @USER taoHouChao
 * @DATE 19:40 2025/8/14
 */
@Slf4j
public class DefenseAssessmentHandshakeInterceptor implements HandshakeInterceptor {
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        log.info("beforeHandshake");
        String token = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        String studentId = Optional.ofNullable(StpUtil.getLoginIdByToken(token)).map(Object::toString).orElse("");
        if (StrUtil.isNotBlank(studentId)) {
            attributes.put("studentId", studentId);
            log.info("websocket handshake success, studentId:{}", studentId);
            return true;
        }
        log.warn("websocket handshake unauthorized");
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        log.info("afterHandshake");
        String token = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        response.getHeaders().add("Sec-WebSocket-Protocol", token);
    }
}
