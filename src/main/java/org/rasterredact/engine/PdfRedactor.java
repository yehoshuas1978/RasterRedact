// Exposes the hash-bound raster-first PDF redaction operation.
// Implementations must fail closed and return only verified output.
package org.rasterredact.engine;

public interface PdfRedactor {

    RedactionResult redact(byte[] pdfBytes, RedactSpec spec);
}
