package system.assessment.defense.infrastructure.config;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import system.assessment.defense.application.service.StudentAssessmentService;
import system.assessment.defense.interfaces.rest.websocket.DefenseAssessmentHandler;
import system.assessment.defense.interfaces.rest.websocket.DefenseAssessmentVoiceHandler;
import system.assessment.defense.interfaces.rest.websocket.DefenseAssessmentVoicePlayHandler;

/**
 * @USER taoHouChao
 * @DATE 19:34 2025/8/14
 */
@Slf4j
@Configuration
@EnableWebSocket
public class WebsocketConfig implements WebSocketConfigurer {

    @Resource
    private StudentAssessmentService studentAssessmentService;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new DefenseAssessmentHandler(studentAssessmentService), "/defense-assessment")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new DefenseAssessmentHandshakeInterceptor());

        registry.addHandler(new DefenseAssessmentVoiceHandler(studentAssessmentService), "/voice/defense-assessment")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new DefenseAssessmentHandshakeInterceptor());

        registry.addHandler(new DefenseAssessmentVoicePlayHandler(studentAssessmentService), "/voice-play/defense-assessment")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new DefenseAssessmentHandshakeInterceptor());
    }
}
