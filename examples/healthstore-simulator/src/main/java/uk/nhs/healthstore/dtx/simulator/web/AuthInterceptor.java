package uk.nhs.healthstore.dtx.simulator.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// The caller's identity comes from the token alone, never from the request.
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String CALLER_ODS = "caller.ods";

    private final TokenIssuer tokens;

    public AuthInterceptor(TokenIssuer tokens) {
        this.tokens = tokens;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Optional<String> ods = tokens.resolve(header.substring(7));
            if (ods.isPresent()) {
                request.setAttribute(CALLER_ODS, ods.get());
                return true;
            }
        }
        throw new NoAccessException();
    }
}
