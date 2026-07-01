// Exposes the authenticated multipart PDF redaction boundary and liveness endpoint.
// Delegates all PDF processing to PdfRedactionService and returns verified bytes only.
package com.susswein.owlmask.pdf.rest;

import com.susswein.owlmask.pdf.config.PdfServiceProperties;
import com.susswein.owlmask.pdf.dto.HealthResponse;
import com.susswein.owlmask.pdf.service.PdfRedactionService;
import com.susswein.owlmask.share.pdf.RedactSpec;
import com.susswein.owlmask.share.pdf.RedactionResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
public class PdfRedactionController {

    private final PdfRedactionService redactionService;
    private final PdfServiceProperties properties;

    public PdfRedactionController(PdfRedactionService redactionService, PdfServiceProperties properties) {
        this.redactionService = redactionService;
        this.properties = properties;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("healthy", "owlmask-pdf");
    }

    @PostMapping(path = "/pdf/redact", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> redact(
            @RequestPart("file") MultipartFile file,
            @RequestPart("spec") RedactSpec spec) throws IOException {
        if (file.isEmpty() || file.getSize() > properties.maxInputBytes()) {
            throw new com.susswein.owlmask.share.pdf.PdfRedactionException(
                    "PDF input size is outside the configured limit");
        }
        RedactionResult result = redactionService.redact(file.getBytes(), spec);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=redacted.pdf")
                .header("X-Redaction-Verified", Boolean.toString(result.verified()))
                .header("X-Pages-Rasterized", Integer.toString(result.pagesRasterized()))
                .header("X-Regions-Applied", Integer.toString(result.regionsApplied()))
                .body(result.pdfBytes());
    }
}
