package system.assessment.defense.application.manage;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for FIX-A.
 * <p>
 * Bug: {@code transcriptFuture} was created only in {@code startTurn} and {@code completeTranscript}
 * only ever {@code complete()}-ed it (never re-created). So when a follow-up turn re-sent
 * {@code input_audio_buffer.commit} and awaited the future, it observed an already-completed
 * (stale, empty) future — the realtime transcript for every follow-up answer was always empty,
 * forcing the slower offline Gemini ASR fallback.
 * <p>
 * The fix resets the future before each commit. Because {@code RealtimeSpeechSession} is a private
 * inner class, this test drives it via reflection on its declared members only.
 */
class RealtimeSpeechSessionTranscriptTest {

    private Object newSession() throws Exception {
        Class<?> clazz = Class.forName(
                "system.assessment.defense.application.manage.RealtimeSpeechManager$RealtimeSpeechSession");
        Constructor<?> ctor = clazz.getDeclaredConstructor(Integer.class, WebSocketSession.class);
        ctor.setAccessible(true);
        return ctor.newInstance(1, null);
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<String> transcriptFuture(Object session) throws Exception {
        Field f = session.getClass().getDeclaredField("transcriptFuture");
        f.setAccessible(true);
        return (CompletableFuture<String>) f.get(session);
    }

    private void invoke(Object session, String method) throws Exception {
        Method m = session.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        m.invoke(session);
    }

    private void completeTranscript(Object session, String value) throws Exception {
        Method m = session.getClass().getDeclaredMethod("completeTranscript", String.class);
        m.setAccessible(true);
        m.invoke(session, value);
    }

    @Test
    void resetTranscriptFutureYieldsFreshFutureForNextTurn() throws Exception {
        Object session = newSession();
        invoke(session, "startTurn");

        // First turn: transcript completes the original future.
        CompletableFuture<String> first = transcriptFuture(session);
        completeTranscript(session, "first answer");
        assertThat(first.get()).isEqualTo("first answer");
        assertThat(first.isDone()).isTrue();

        // Before the fix, a second completeTranscript acted on the same already-done future and
        // could not change its value. resetTranscriptFuture() must swap in a brand-new, incomplete
        // future so the follow-up turn's transcript is observable.
        invoke(session, "resetTranscriptFuture");
        CompletableFuture<String> second = transcriptFuture(session);
        assertThat(second).isNotSameAs(first);
        assertThat(second.isDone()).isFalse();

        completeTranscript(session, "follow-up answer");
        assertThat(second.get()).isEqualTo("follow-up answer");
    }
}
