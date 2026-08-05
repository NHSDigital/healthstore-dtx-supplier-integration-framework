package uk.nhs.healthstore.dtx.simulator.web;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

// Stands in for the interim Cognito arrangement.
@Component
public class TokenIssuer {

    public record Token(String accessToken, int expiresIn) {
    }

    private record Issued(String odsCode, Instant expiry) {
    }

    private final AuthSettings settings;
    private final Clock clock;
    private final Map<String, Issued> tokens = new ConcurrentHashMap<>();

    public TokenIssuer(AuthSettings settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    public Optional<Token> issue(String clientId, String clientSecret) {
        return settings.suppliers().stream()
                .filter(s -> s.clientId().equals(clientId) && s.clientSecret().equals(clientSecret))
                .findFirst()
                .map(s -> {
                    String token = UUID.randomUUID().toString();
                    tokens.put(token, new Issued(
                            s.odsCode(), Instant.now(clock).plusSeconds(settings.tokenTtlSeconds())));
                    return new Token(token, settings.tokenTtlSeconds());
                });
    }

    public Optional<String> resolve(String token) {
        return Optional.ofNullable(tokens.get(token))
                .filter(issued -> Instant.now(clock).isBefore(issued.expiry()))
                .map(Issued::odsCode);
    }
}
