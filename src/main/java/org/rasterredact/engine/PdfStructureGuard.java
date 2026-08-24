// Enforces page, encryption, decoded-stream, and estimated render limits before redaction.
// Traverses PDFBox COS streams with bounded reads so malformed inputs fail closed.
package org.rasterredact.engine;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSObjectKey;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.IOException;
import java.io.InputStream;

final class PdfStructureGuard {

    void validate(byte[] input, PDDocument document, int dpi, RedactionLimits limits) throws IOException {
        if (input.length > limits.maxInputBytes()) {
            throw new PdfRedactionException("PDF exceeds the maximum input size");
        }
        if (document.isEncrypted()) {
            throw new PdfRedactionException("Encrypted PDFs are not accepted by the v1 redactor");
        }
        if (document.getNumberOfPages() == 0 || document.getNumberOfPages() > limits.maxPages()) {
            throw new PdfRedactionException("PDF page count is outside the configured limit");
        }
        if (dpi < limits.minDpi() || dpi > limits.maxDpi()) {
            throw new PdfRedactionException("Requested DPI is outside the configured limit");
        }
        validateRenderedPixelBudget(document, dpi, limits.maxRenderedPixels());
        validateDecodedStreams(document, limits.maxDecodedStreamBytes());
    }

    private void validateRenderedPixelBudget(PDDocument document, int dpi, long maximum) {
        long total = 0;
        for (PDPage page : document.getPages()) {
            PDRectangle crop = page.getCropBox();
            double width = crop.getWidth();
            double height = crop.getHeight();
            long pagePixels = (long) Math.ceil(width * dpi / 72.0)
                    * (long) Math.ceil(height * dpi / 72.0);
            if (pagePixels <= 0 || total > maximum - pagePixels) {
                throw new PdfRedactionException("PDF exceeds the rendered pixel budget");
            }
            total += pagePixels;
        }
    }

    private void validateDecodedStreams(PDDocument document, long maximum) throws IOException {
        long total = 0;
        byte[] buffer = new byte[8192];
        for (COSObjectKey key : document.getDocument().getXrefTable().keySet()) {
            COSObject object = document.getDocument().getObjectFromPool(key);
            COSBase value = object.getObject();
            if (!(value instanceof COSStream stream)) {
                continue;
            }
            try (InputStream input = stream.createInputStream()) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (total > maximum - read) {
                        throw new PdfRedactionException("PDF exceeds the decoded stream budget");
                    }
                    total += read;
                }
            }
        }
    }
}
