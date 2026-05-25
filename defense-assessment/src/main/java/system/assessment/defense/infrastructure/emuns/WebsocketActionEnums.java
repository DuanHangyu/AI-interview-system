package system.assessment.defense.infrastructure.emuns;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

@Getter
@AllArgsConstructor
public enum WebsocketActionEnums{
    PING("ping"),
    START_DEFENSE("start-defense"),
    END_DEFENSE("end-defense"),
    START_ASSESSMENT("start-assessment"),
    END_ASSESSMENT("end-assessment"),
    START_ANSWER("start-answer"),
    END_ANSWER("end-answer"),
    GENERATE_QUESTION("generate_question"),
    STOP("stop"),
    ;

    private final String action;

    public static boolean isPing(String action){
        return Objects.equals(PING.action, action);
    }
}
