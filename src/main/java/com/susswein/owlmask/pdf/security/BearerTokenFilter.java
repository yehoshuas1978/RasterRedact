// Enforces a shared bearer token on every endpoint except service health.
// Uses constant-time byte comparison and never logs credentials or PDF content.
package com.susswein.owlmask.pdf.security;

import com.susswein.owlmask.pdf.config.PdfServiceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

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
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String supplied = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : "";
        if (configured == null || configured.isBlank()) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "PDF auth token is not configured");
            return;
        }
        if (!MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid bearer token");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
