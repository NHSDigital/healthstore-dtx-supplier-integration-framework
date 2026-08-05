package uk.nhs.healthstore.dtx.simulator.web;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Errors are OAuth 2.0 error JSON, not OperationOutcome, so parameter presence
// is checked here rather than left to the global handlers.
@RestController
public class OAuthTokenController {

    private final TokenIssuer issuer;

    public OAuthTokenController(TokenIssuer issuer) {
        this.issuer = issuer;
    }

    @PostMapping(path = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(@RequestBody MultiValueMap<String, String> form) {
        String grantType = form.getFirst("grant_type");
        String clientId = form.getFirst("client_id");
        String clientSecret = form.getFirst("client_secret");
        if (grantType == null || clientId == null || clientSecret == null) {
            return error("invalid_request");
        }
        if (!"client_credentials".equals(grantType)) {
            return error("unsupported_grant_type");
        }
        return issuer.issue(clientId, clientSecret)
                .map(token -> ResponseEntity.ok(Map.<String, Object>of(
                        "access_token", token.accessToken(),
                        "token_type", "Bearer",
                        "expires_in", token.expiresIn())))
                .orElseGet(() -> error("invalid_client"));
    }

    private static ResponseEntity<Map<String, Object>> error(String code) {
        return ResponseEntity.badRequest().body(Map.of("error", code));
    }
}
