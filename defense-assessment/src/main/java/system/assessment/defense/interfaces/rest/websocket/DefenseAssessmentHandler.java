package system.assessment.defense.interfaces.rest.websocket;

import cn.hutool.json.JSONUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import system.assessment.defense.application.dto.WebSocketResponse;
import system.assessment.defense.application.dto.WebsocketMessageDTO;
import system.assessment.defense.application.manage.WebsocketManager;
import system.assessment.defense.application.service.StudentAssessmentService;
import system.assessment.defense.infrastructure.emuns.WebsocketActionEnums;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * @USER taoHouChao
 * @DATE 19:37 2025/8/14
 */
@AllArgsConstructor
@Slf4j
public class DefenseAssessmentHandler extends AbstractWebSocketHandler {

    private final StudentAssessmentService studentAssessmentService;

    private static final Cache<String, Boolean> DEBOUNCE_CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(3, TimeUnit.SECONDS)
            .maximumSize(10000)
            .build();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String studentIdStr = session.getAttributes().get("studentId").toString();
        Integer studentId = Integer.parseInt(studentIdStr);
        WebsocketManager.addTextSession(studentId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("voice关闭连接");
        String studentIdStr = session.getAttributes().get("studentId").toString();
        Integer studentId = Integer.parseInt(studentIdStr);
        WebsocketManager.removeTextSession(studentId);
    }

    @Override
    public void handleTransportError(WebSocketSession session, @NotNull Throwable exception) {
        log.error("websocket连接出错，sessionId:{}", session.getId(), exception);
        String userId = session.getAttributes().get("studentId").toString();
        Integer studentId = Integer.parseInt(userId);
        WebsocketManager.removeTextSession(studentId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WebsocketMessageDTO messageDTO = JSONUtil.toBean(message.getPayload(), WebsocketMessageDTO.class);
        if (WebsocketActionEnums.isPing(messageDTO.getAction())) {
            log.info("收到 ping 消息");
            return;
        }
        String userId = session.getAttributes().get("studentId").toString();
        Integer studentId = Integer.parseInt(userId);
        log.info("收到 websocket 消息: {}, time:{}", messageDTO, System.currentTimeMillis());
        // 防抖防刷
        String key = String.format("%d:%s:%s", studentId, messageDTO.getAction(), messageDTO.getMessage());
        Boolean exist = DEBOUNCE_CACHE.asMap().putIfAbsent(key, true);
        if (exist != null) {
            log.info("消息去抖处理，key:{}", key);
            return;
        }
        try {
            studentAssessmentService.handleWebsocketMessage(studentId, messageDTO, content -> sendMessage(session, content));
        } catch (Exception e) {
            log.error("处理WebSocket消息异常, studentId:{}, action:{}", studentId, messageDTO.getAction(), e);
            sendMessage(session, WebSocketResponse.ofError("处理消息失败：" + e.getMessage()));
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return true;
    }

    private void sendMessage(WebSocketSession session, WebSocketResponse message) {
        try {
            session.sendMessage(new TextMessage(JSONUtil.toJsonStr(message)));
        } catch (IOException e) {
            log.error("发送消息失败：{}", e.getMessage());
        }
    }
}
