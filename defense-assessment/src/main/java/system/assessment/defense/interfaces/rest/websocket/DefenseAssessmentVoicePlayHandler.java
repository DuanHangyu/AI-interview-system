package system.assessment.defense.interfaces.rest.websocket;

import cn.hutool.json.JSONUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import system.assessment.defense.application.dto.WebsocketMessageDTO;
import system.assessment.defense.application.manage.WebsocketManager;
import system.assessment.defense.application.service.StudentAssessmentService;
import system.assessment.defense.infrastructure.emuns.WebsocketActionEnums;

/**
 * @USER taoHouChao
 * @DATE 22:38 2025/8/14
 */
@AllArgsConstructor
@Slf4j
public class DefenseAssessmentVoicePlayHandler extends AbstractWebSocketHandler {

    private final StudentAssessmentService studentAssessmentService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String studentIdStr = session.getAttributes().get("studentId").toString();
        Integer studentId = Integer.parseInt(studentIdStr);
        WebsocketManager.addVoicePlaySession(studentId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("voice关闭连接");
        String studentIdStr = session.getAttributes().get("studentId").toString();
        Integer studentId = Integer.parseInt(studentIdStr);
        WebsocketManager.removeVoicePayload(studentId);
    }

    @Override
    public void handleTransportError(WebSocketSession session, @NotNull Throwable exception) {
        log.error("websocket连接出错，sessionId:{}", session.getId(), exception);
        String userId = session.getAttributes().get("studentId").toString();
        Integer studentId = Integer.parseInt(userId);
        WebsocketManager.removeVoicePayload(studentId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        WebsocketMessageDTO messageDTO = JSONUtil.toBean(message.getPayload(), WebsocketMessageDTO.class);
        if (WebsocketActionEnums.isPing(messageDTO.getAction())) {
            log.info("收到 ping 消息");
            return;
        }
        if (messageDTO.stop()) {
            String userId = session.getAttributes().get("studentId").toString();
            Integer studentId = Integer.parseInt(userId);
            WebsocketManager.interruptVoiceSending(studentId);
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return true;
    }
}
