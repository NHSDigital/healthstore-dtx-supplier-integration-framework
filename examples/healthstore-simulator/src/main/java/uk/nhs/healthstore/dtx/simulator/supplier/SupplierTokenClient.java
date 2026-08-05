package uk.nhs.healthstore.dtx.simulator.supplier;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class SupplierTokenClient {

    public record Bearer(String token, String how) {
    }

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;

    private String token;
    private Instant expiry;

    public SupplierTokenClient(
            Clock clock,
            @Value("${simulator.supplier-base-url}") String supplierBaseUrl,
            @Value("${simulator.supplier-client-id}") String clientId,
            @Value("${simulator.supplier-client-secret}") String clientSecret) {
        this.restClient = RestClient.builder().baseUrl(supplierBaseUrl).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    // Expiry is judged by the simulator clock, so under simulator.fixed-time a
    // cached token never expires locally and the 401 re-acquire path takes over.
    public synchronized Bearer bearer() {
        if (token != null && Instant.now(clock).isBefore(expiry.minusSeconds(5))) {
            return new Bearer(token, "cached");
        }
        acquire();
        return new Bearer(token, "acquired");
    }

    public synchronized Bearer reacquire() {
        acquire();
        return new Bearer(token, "reacquired");
    }

    public synchronized void reset() {
        token = null;
        expiry = null;
    }

    private void acquire() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        Map<String, Object> response = restClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        token = (String) response.get("access_token");
        expiry = Instant.now(clock).plusSeconds(((Number) response.get("expires_in")).longValue());
    }
}
