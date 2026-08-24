// Defines the bounded HTTP-facing PDF redaction operation.
// Implementations delegate mechanical redaction to owlmask-share-pdf.
package org.rasterredact.api.service;

import org.rasterredact.engine.RedactSpec;
import org.rasterredact.engine.RedactionResult;

public interface PdfRedactionService {

    RedactionResult redact(byte[] pdfBytes, RedactSpec spec);
}
