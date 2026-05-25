package system.assessment.defense.application.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RealtimeSpeechEventFactory {

    private static final String TRANSCRIPTION_MODEL = "qwen3-asr-flash-realtime";
    private static final String TRANSCRIPTION_COMPLETED = "conversation.item.input_audio_transcription.completed";

    private final ObjectMapper objectMapper;

    public String sessionUpdate(String voice, String instructions) {
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("modalities", List.of("text"));
        session.put("voice", StringUtils.defaultIfBlank(voice, "Ethan"));
        session.put("input_audio_format", "pcm");
        session.put("output_audio_format", "pcm");
        session.put("instructions", StringUtils.defaultIfBlank(instructions, "请准确转写学生在面试中的语音回答。"));
        session.put("turn_detection", null);
        session.put("input_audio_transcription", Map.of("model", TRANSCRIPTION_MODEL));

        return toJson(Map.of(
                "event_id", eventId(),
                "type", "session.update",
                "session", session
        ));
    }

    public String inputAudioAppend(byte[] audioChunk) {
        return toJson(Map.of(
                "event_id", eventId(),
                "type", "input_audio_buffer.append",
                "audio", Base64.getEncoder().encodeToString(audioChunk)
        ));
    }

    public String inputAudioCommit() {
        return toJson(Map.of(
                "event_id", eventId(),
                "type", "input_audio_buffer.commit"
        ));
    }

    public String inputAudioClear() {
        return toJson(Map.of(
                "event_id", eventId(),
                "type", "input_audio_buffer.clear"
        ));
    }

    public Optional<String> extractCompletedTranscript(String serverEvent) {
        try {
            JsonNode root = objectMapper.readTree(serverEvent);
            if (!Objects.equals(root.path("type").asText(), TRANSCRIPTION_COMPLETED)) {
                return Optional.empty();
            }
            String transcript = root.path("transcript").asText("");
            return StringUtils.isBlank(transcript) ? Optional.empty() : Optional.of(transcript.trim());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private String toJson(Map<String, Object> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (IOException e) {
            throw new IllegalStateException("Realtime event serialization failed", e);
        }
    }

    private String eventId() {
        return "event_" + UUID.randomUUID();
    }
}
