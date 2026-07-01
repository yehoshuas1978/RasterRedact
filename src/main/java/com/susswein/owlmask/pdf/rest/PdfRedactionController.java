// Exposes the authenticated multipart PDF redaction boundary and liveness endpoint.
// Delegates all PDF processing to PdfRedactionService and returns verified bytes only.
package com.susswein.owlmask.pdf.rest;

import com.susswein.owlmask.pdf.dto.HealthResponse;
import com.susswein.owlmask.pdf.service.PdfRedactionService;
import com.susswein.owlmask.share.pdf.RedactSpec;
import com.susswein.owlmask.share.pdf.RedactionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "PDF Redaction", description = "Internal raster-first PDF redaction operations")
public class PdfRedactionController {

    private final PdfRedactionService redactionService;

    public PdfRedactionController(PdfRedactionService redactionService) {
        this.redactionService = redactionService;
    }

    @GetMapping("/health")
    @Operation(summary = "Check liveness", description = "Returns liveness without processing a document")
    public HealthResponse health() {
        return new HealthResponse("healthy", "owlmask-pdf");
    }

    @PostMapping(path = "/pdf/redact", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Redact a PDF", description = "Reconstructs a hash-bound PDF as verified image-only pages")
    public ResponseEntity<byte[]> redact(
            @RequestPart("file") MultipartFile file,
            @RequestPart("spec") RedactSpec spec) throws IOException {
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
