package uk.nhs.healthstore.dtx.referencesupplier;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

// Tokens are opaque, so random values rather than JWTs.
@Component
public class TokenStore {

    private final Map<String, Instant> tokens = new ConcurrentHashMap<>();

    public String issue(Duration ttl) {
        Instant now = Instant.now();
        tokens.values().removeIf(expiry -> now.isAfter(expiry));
        String token = UUID.randomUUID().toString();
        tokens.put(token, now.plus(ttl));
        return token;
    }

    public boolean valid(String token) {
        Instant expiry = tokens.get(token);
        return expiry != null && Instant.now().isBefore(expiry);
    }
}
