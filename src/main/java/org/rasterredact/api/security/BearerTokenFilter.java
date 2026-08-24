package org.rasterredact.api.security;

import org.rasterredact.api.config.PdfServiceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.security.MessageDigest;
import java.io.IOException;

@Component
public class BearerTokenFilter extends OncePerRequestFilter {

    private final PdfServiceProperties properties;

    public BearerTokenFilter(PdfServiceProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/health".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String configured = properties.authToken();
        if (configured == null || configured.isBlank()) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "PDF auth token is not configured");
            return;
        }
        
        String authHeader = request.getHeader("Authorization");
        String presented = "";
        if (authHeader != null && authHeader.toLowerCase().startsWith("bearer ")) {
            presented = authHeader.substring(7).trim();
        }
        
        if (!MessageDigest.isEqual(configured.getBytes(), presented.getBytes())) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid bearer token");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
