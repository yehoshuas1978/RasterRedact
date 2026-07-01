// Binds authentication, timeout, concurrency, and PDF resource limits from configuration.
// Consumed by request authentication and the bounded redaction service.
package com.susswein.owlmask.pdf.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "owlmask.pdf")
public record PdfServiceProperties(
        String authToken,
        long maxInputBytes,
        int maxPages,
        int minDpi,
        int maxDpi,
        long maxRenderedPixels,
        long maxDecodedStreamBytes,
        int maxRegions,
        int timeoutSeconds,
        int maxConcurrency
) {
    public PdfServiceProperties {
        if (authToken == null || authToken.length() < 32) {
            throw new IllegalArgumentException("owlmask.pdf.auth-token must contain at least 32 characters");
        }
        if (maxInputBytes <= 0 || maxPages <= 0 || minDpi <= 0 || maxDpi < minDpi
                || maxRenderedPixels <= 0 || maxDecodedStreamBytes <= 0
                || maxRegions <= 0 || timeoutSeconds <= 0 || maxConcurrency <= 0) {
            throw new IllegalArgumentException("owlmask.pdf resource limits must be positive and internally consistent");
        }
    }
}
