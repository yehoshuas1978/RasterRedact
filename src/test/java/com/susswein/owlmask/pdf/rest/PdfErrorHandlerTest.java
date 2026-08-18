// Verifies the error mapping contract: fail-closed redaction is 4xx, never a success,
// and no error body carries the originating exception's text or any PDF payload.
package com.susswein.owlmask.pdf.rest;

import com.susswein.owlmask.share.pdf.PdfRedactionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PdfErrorHandlerTest {

    private final PdfErrorHandler handler = new PdfErrorHandler();

    @Test
    void failClosedRedactionBecomesUnprocessableEntity() {
        ProblemDetail detail = handler.redactionFailure(
                new PdfRedactionException("Encrypted PDFs are not accepted by the v1 redactor"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), detail.getStatus());
        assertEquals("PDF redaction failed closed", detail.getTitle());
        assertEquals("Encrypted PDFs are not accepted by the v1 redactor", detail.getDetail());
    }

    @Test
    void unexpectedFailureBecomesBadRequestWithoutEchoingTheCause() {
        // A parser failure can carry document bytes or a spool path in its message; the
        // generic branch must answer with a fixed string rather than the cause's text.
        String leaky = "failed at /var/spool/upload-8823.pdf: %PDF-1.7 Patient SSN 123-45-6789";

        ProblemDetail detail = handler.unexpectedFailure(new IOException(leaky));

        assertEquals(HttpStatus.BAD_REQUEST.value(), detail.getStatus());
        assertEquals("Invalid PDF redaction request", detail.getTitle());
        assertNotNull(detail.getDetail());
        assertFalse(detail.getDetail().contains("123-45-6789"), "error body leaked document content");
        assertFalse(detail.getDetail().contains("%PDF"), "error body leaked PDF payload");
        assertFalse(detail.getDetail().contains("/var/spool"), "error body leaked a server path");
    }

    @Test
    void neitherBranchEverReportsSuccess() {
        // ProblemDetail is the only response shape here, so a regression that mapped a
        // failure to 2xx would hand the caller an empty body with a success status.
        assertFalse(HttpStatus.valueOf(handler.redactionFailure(
                new PdfRedactionException("boom")).getStatus()).is2xxSuccessful());
        assertFalse(HttpStatus.valueOf(handler.unexpectedFailure(
                new IllegalStateException("boom")).getStatus()).is2xxSuccessful());
    }
}
