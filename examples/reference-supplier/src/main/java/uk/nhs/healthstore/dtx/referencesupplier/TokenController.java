package uk.nhs.healthstore.dtx.referencesupplier;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.healthstore.dtx.referencesupplier.api.AuthenticationApi;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.PostOAuthToken200Response;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.PostOAuthToken400Response;

@RestController
public class TokenController implements AuthenticationApi {

    private final TokenStore tokens;
    private final String clientId;
    private final String clientSecret;
    private final int tokenTtlSeconds;

    public TokenController(
            TokenStore tokens,
            @Value("${supplier.client-id}") String clientId,
            @Value("${supplier.client-secret}") String clientSecret,
            @Value("${supplier.token-ttl-seconds}") int tokenTtlSeconds) {
        this.tokens = tokens;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    @Override
    public ResponseEntity<PostOAuthToken200Response> postOAuthToken(
            String grantType, String clientId, String clientSecret) {
        if (!"client_credentials".equals(grantType)) {
            throw new OAuthError(PostOAuthToken400Response.ErrorEnum.UNSUPPORTED_GRANT_TYPE);
        }
        if (!this.clientId.equals(clientId) || !this.clientSecret.equals(clientSecret)) {
            throw new OAuthError(PostOAuthToken400Response.ErrorEnum.INVALID_CLIENT);
        }
        return ResponseEntity.ok(new PostOAuthToken200Response(
                tokens.issue(Duration.ofSeconds(tokenTtlSeconds)),
                PostOAuthToken200Response.TokenTypeEnum.BEARER,
                tokenTtlSeconds));
    }

    private static final class OAuthError extends RuntimeException {
        private final PostOAuthToken400Response.ErrorEnum error;

        private OAuthError(PostOAuthToken400Response.ErrorEnum error) {
            this.error = error;
        }
    }

    @ExceptionHandler(OAuthError.class)
    ResponseEntity<PostOAuthToken400Response> oauthError(OAuthError e) {
        return ResponseEntity.badRequest().body(new PostOAuthToken400Response(e.error));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<PostOAuthToken400Response> missingParameter() {
        return ResponseEntity.badRequest()
                .body(new PostOAuthToken400Response(PostOAuthToken400Response.ErrorEnum.INVALID_REQUEST));
    }
}
