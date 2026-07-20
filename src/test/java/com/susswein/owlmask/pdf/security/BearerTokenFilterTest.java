// Unit tests for BearerTokenFilter: header parsing, constant-time match outcomes,
// the health-check bypass, and fail-closed behavior when no token is configured.
package com.susswein.owlmask.pdf.security;

import com.susswein.owlmask.pdf.config.PdfServiceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BearerTokenFilterTest {

    private static final String TOKEN = "test-secret-that-is-at-least-32-chars";

    private static PdfServiceProperties properties(String token) {
        return new PdfServiceProperties(token, 1024, 10, 72, 150,
                50_000_000, 50_000_000, 100, 30, 1);
    }

    private MockHttpServletResponse run(BearerTokenFilter filter, MockHttpServletRequest request,
            MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void passesRequestsCarryingTheConfiguredBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/redact");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(new BearerTokenFilter(properties(TOKEN)), request, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void rejectsWrongMissingAndMalformedTokens() throws Exception {
        BearerTokenFilter filter = new BearerTokenFilter(properties(TOKEN));

        MockHttpServletRequest wrong = new MockHttpServletRequest("POST", "/redact");
        wrong.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN + "x");
        assertEquals(401, run(filter, wrong, new MockFilterChain()).getStatus());

        MockHttpServletRequest missing = new MockHttpServletRequest("POST", "/redact");
        assertEquals(401, run(filter, missing, new MockFilterChain()).getStatus());

        MockHttpServletRequest basicScheme = new MockHttpServletRequest("POST", "/redact");
        basicScheme.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + TOKEN);
        assertEquals(401, run(filter, basicScheme, new MockFilterChain()).getStatus());

        MockHttpServletRequest truncatedPrefix = new MockHttpServletRequest("POST", "/redact");
        truncatedPrefix.addHeader(HttpHeaders.AUTHORIZATION, TOKEN);
        assertEquals(401, run(filter, truncatedPrefix, new MockFilterChain()).getStatus());
    }

    @Test
    void allowsHealthChecksWithoutAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.setRequestURI("/health");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(new BearerTokenFilter(properties(TOKEN)), request, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void failsClosedWithServiceUnavailableWhenNoTokenIsConfigured() throws Exception {
        // A whitespace-only token satisfies the record's length check but is blank to the filter.
        PdfServiceProperties broken = properties(" ".repeat(32));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/redact");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(new BearerTokenFilter(broken), request, chain);

        assertEquals(503, response.getStatus());
        assertNull(chain.getRequest());
    }
}
