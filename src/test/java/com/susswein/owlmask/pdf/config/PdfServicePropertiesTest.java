// Verifies the configuration record's fail-fast validation: token strength and
// positive, internally consistent resource limits.
package com.susswein.owlmask.pdf.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PdfServicePropertiesTest {

    private static final String TOKEN = "test-secret-that-is-at-least-32-chars";

    private static PdfServiceProperties build(String token, long maxInputBytes, int maxPages,
            int minDpi, int maxDpi, int timeoutSeconds, int maxConcurrency) {
        return new PdfServiceProperties(token, maxInputBytes, maxPages, minDpi, maxDpi,
                50_000_000, 50_000_000, 100, timeoutSeconds, maxConcurrency);
    }

    @Test
    void acceptsAStrongTokenWithConsistentLimits() {
        assertDoesNotThrow(() -> build(TOKEN, 1024, 10, 72, 150, 30, 2));
        assertDoesNotThrow(() -> build("x".repeat(32), 1024, 10, 72, 72, 1, 1));
    }

    @Test
    void rejectsMissingOrShortAuthTokens() {
        assertThrows(IllegalArgumentException.class, () -> build(null, 1024, 10, 72, 150, 30, 1));
        assertThrows(IllegalArgumentException.class, () -> build("x".repeat(31), 1024, 10, 72, 150, 30, 1));
    }

    @Test
    void rejectsNonPositiveOrInconsistentResourceLimits() {
        assertThrows(IllegalArgumentException.class, () -> build(TOKEN, 0, 10, 72, 150, 30, 1));
        assertThrows(IllegalArgumentException.class, () -> build(TOKEN, 1024, 0, 72, 150, 30, 1));
        // A DPI ceiling below the floor leaves no valid rendering resolution.
        assertThrows(IllegalArgumentException.class, () -> build(TOKEN, 1024, 10, 150, 72, 30, 1));
        assertThrows(IllegalArgumentException.class, () -> build(TOKEN, 1024, 10, 72, 150, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> build(TOKEN, 1024, 10, 72, 150, 30, 0));
    }
}
