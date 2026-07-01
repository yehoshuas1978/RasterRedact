// Defines the bounded HTTP-facing PDF redaction operation.
// Implementations delegate mechanical redaction to owlmask-share-pdf.
package com.susswein.owlmask.pdf.service;

import com.susswein.owlmask.share.pdf.RedactSpec;
import com.susswein.owlmask.share.pdf.RedactionResult;

public interface PdfRedactionService {

    RedactionResult redact(byte[] pdfBytes, RedactSpec spec);
}
