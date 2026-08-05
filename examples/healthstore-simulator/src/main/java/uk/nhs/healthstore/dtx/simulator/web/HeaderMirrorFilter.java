package uk.nhs.healthstore.dtx.simulator.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class HeaderMirrorFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        mirror(request, response, "X-Request-ID");
        mirror(request, response, "X-Correlation-ID");
        filterChain.doFilter(request, response);
    }

    private void mirror(HttpServletRequest request, HttpServletResponse response, String header) {
        String value = request.getHeader(header);
        if (value != null) {
            response.setHeader(header, value);
        }
    }
}
