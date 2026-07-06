package messenger.commonlibs.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static messenger.commonlibs.Constants.INTERNAL_KEY_HEADER;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalApiKeyFilter extends OncePerRequestFilter {

    @Value("${internal.api.key}")
    private String internalApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(INTERNAL_KEY_HEADER);
        if (key == null || !key.equals(internalApiKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("text/plain");
            response.getWriter().write("Missing or invalid internal API key");
            return;
        }
        chain.doFilter(request, response);
    }
}
