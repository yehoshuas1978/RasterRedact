// Returns verified image-only PDF bytes and mechanical redaction counts.
// Produced only after RasterPdfRedactor completes structural verification.
package org.rasterredact.engine;

public record RedactionResult(
        byte[] pdfBytes,
        boolean verified,
        int pagesRasterized,
        int regionsApplied
) {
}
