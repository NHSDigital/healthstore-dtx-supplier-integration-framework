package uk.nhs.healthstore.dtx.simulator.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
        Instant now = Instant.now(clock);
        tokens.entrySet().removeIf(e -> !now.isBefore(e.getValue().expiry()));
        return settings.suppliers().stream()
                .filter(s -> {
                    boolean idOk = matches(s.clientId(), clientId);
                    boolean secretOk = matches(s.clientSecret(), clientSecret);
                    return idOk && secretOk;
                })
                .findFirst()
                .map(s -> {
                    String token = UUID.randomUUID().toString();
                    tokens.put(token, new Issued(
                            s.odsCode(), now.plusSeconds(settings.tokenTtlSeconds())));
                    return new Token(token, settings.tokenTtlSeconds());
                });
    }

    public Optional<String> resolve(String token) {
        return Optional.ofNullable(tokens.get(token))
                .filter(issued -> Instant.now(clock).isBefore(issued.expiry()))
                .map(Issued::odsCode);
    }

    public void reset() {
        tokens.clear();
    }

    private static boolean matches(String expected, String provided) {
        return provided != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
