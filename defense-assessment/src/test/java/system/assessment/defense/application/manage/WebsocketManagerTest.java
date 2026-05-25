package system.assessment.defense.application.manage;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebsocketManagerTest {

    @Test
    void staleTextSessionCloseDoesNotRemoveReplacementSession() {
        int studentId = 1001;
        WebSocketSession oldSession = openSession();
        WebSocketSession replacementSession = openSession();

        WebsocketManager.addTextSession(studentId, oldSession);
        WebsocketManager.addTextSession(studentId, replacementSession);
        WebsocketManager.removeTextSession(studentId, oldSession);

        assertThat(WebsocketManager.getTextSession(studentId)).isSameAs(replacementSession);

        WebsocketManager.removeTextSession(studentId, replacementSession);
        assertThat(WebsocketManager.getTextSession(studentId)).isNull();
    }

    @Test
    void staleVoicePlaySessionCloseDoesNotRemoveReplacementSession() {
        int studentId = 1002;
        WebSocketSession oldSession = openSession();
        WebSocketSession replacementSession = openSession();

        WebsocketManager.addVoicePlaySession(studentId, oldSession);
        WebsocketManager.addVoicePlaySession(studentId, replacementSession);
        WebsocketManager.removeVoicePayload(studentId, oldSession);

        assertThat(WebsocketManager.getVoicePlaySession(studentId)).isSameAs(replacementSession);

        WebsocketManager.removeVoicePayload(studentId, replacementSession);
        assertThat(WebsocketManager.getVoicePlaySession(studentId)).isNull();
    }

    private WebSocketSession openSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
