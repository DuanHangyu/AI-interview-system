package system.assessment.defense.interfaces.rest.websocket;

import cn.hutool.json.JSONUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import system.assessment.defense.application.dto.WebSocketResponse;
import system.assessment.defense.application.dto.WebsocketMessageDTO;
import system.assessment.defense.application.manage.RealtimeSpeechManager;
import system.assessment.defense.application.manage.WebsocketManager;
import system.assessment.defense.application.service.StudentAssessmentService;
import system.assessment.defense.infrastructure.emuns.WebsocketActionEnums;

import java.io.IOException;

/**
 * @USER taoHouChao
 * @DATE 22:38 2025/8/14
 */
@AllArgsConstructor
@Slf4j
public class DefenseAssessmentVoiceHandler extends AbstractWebSocketHandler {

    private final StudentAssessmentService studentAssessmentService;
    private final RealtimeSpeechManager realtimeSpeechManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String studentIdStr = session.getAttributes().get("studentId").toString();
        Integer studentId = Integer.parseInt(studentIdStr);
        WebsocketManager.addVoiceSession(studentId, session);
        realtimeSpeechManager.open(studentId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("voice关闭连接");
        String studentIdStr = session.getAttributes().get("studentId").toString();
        Integer studentId = Integer.parseInt(studentIdStr);
        WebsocketManager.removeVoiceSession(studentId, session);
        realtimeSpeechManager.close(studentId, session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, @NotNull Throwable exception) {
        log.error("websocket连接出错，sessionId:{}", session.getId(), exception);
        String userId = session.getAttributes().get("studentId").toString();
        Integer studentId = Integer.parseInt(userId);
        WebsocketManager.removeVoiceSession(studentId, session);
        realtimeSpeechManager.close(studentId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        WebsocketMessageDTO messageDTO = JSONUtil.toBean(message.getPayload(), WebsocketMessageDTO.class);
        if (WebsocketActionEnums.isPing(messageDTO.getAction())) {
            log.info("收到 ping 消息");
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        String studentIdStr = session.getAttributes().get("studentId").toString();
        Integer studentId = Integer.parseInt(studentIdStr);
        studentAssessmentService.appendVoice(message.getPayload().asReadOnlyBuffer(), studentId);
        realtimeSpeechManager.appendAudio(studentId, message.getPayload().asReadOnlyBuffer());
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
