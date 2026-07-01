// Maps fail-closed redaction and malformed multipart failures to non-success responses.
// Prevents partial or original PDF bytes from appearing in error bodies.
package com.susswein.owlmask.pdf.rest;

import com.susswein.owlmask.share.pdf.PdfRedactionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PdfErrorHandler {

    @ExceptionHandler(PdfRedactionException.class)
    public ProblemDetail redactionFailure(PdfRedactionException exception) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        detail.setTitle("PDF redaction failed closed");
        detail.setDetail(exception.getMessage());
        return detail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpectedFailure(Exception exception) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Invalid PDF redaction request");
        detail.setDetail("The multipart PDF redaction request could not be processed");
        return detail;
    }
}
