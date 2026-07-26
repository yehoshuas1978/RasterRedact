// Enforces a shared bearer token on every endpoint except service health.
// Credential extraction and constant-time comparison come from
// owlmask-share-security so every OwlMask service treats credentials identically;
// this filter keeps its own fail-closed rejection because it has no Spring
// Security chain behind it to fall back on.
package com.susswein.owlmask.pdf.security;

import com.susswein.owlmask.pdf.config.PdfServiceProperties;
import com.susswein.owlmask.share.security.ApiCredentials;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

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
        if (!ApiCredentials.matches(configured, ApiCredentials.presented(request))) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid bearer token");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
