package uk.nhs.healthstore.dtx.referencesupplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.nhs.healthstore.dtx.referencesupplier.api.AuthenticationApi;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.OperationOutcome;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.OperationOutcomeIssueInner;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.OperationOutcomeIssueInnerDetails;
import uk.nhs.healthstore.dtx.referencesupplier.api.model.OperationOutcomeIssueInnerDetailsCodingInner;

@Component
public class BearerTokenFilter extends OncePerRequestFilter {

    private final TokenStore tokens;
    private final ObjectMapper mapper = new ObjectMapper();

    public BearerTokenFilter(TokenStore tokens) {
        this.tokens = tokens;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return AuthenticationApi.PATH_POST_O_AUTH_TOKEN.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && tokens.valid(header.substring(7))) {
            filterChain.doFilter(request, response);
            return;
        }
        mirror(request, response, "X-Request-ID");
        mirror(request, response, "X-Correlation-ID");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/fhir+json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), noAccess());
    }

    private OperationOutcome noAccess() {
        return new OperationOutcome(
                OperationOutcome.ResourceTypeEnum.OPERATION_OUTCOME,
                List.of(new OperationOutcomeIssueInner(
                        OperationOutcomeIssueInner.SeverityEnum.ERROR,
                        OperationOutcomeIssueInner.CodeEnum.LOGIN)
                        .details(new OperationOutcomeIssueInnerDetails()
                                .coding(List.of(new OperationOutcomeIssueInnerDetailsCodingInner(
                                        OperationOutcomeIssueInnerDetailsCodingInner.CodeEnum.NO_ACCESS))))
                        .diagnostics("No token, or a token that is invalid or expired")));
    }

    private void mirror(HttpServletRequest request, HttpServletResponse response, String header) {
        String value = request.getHeader(header);
        if (value != null) {
            response.setHeader(header, value);
        }
    }
}
