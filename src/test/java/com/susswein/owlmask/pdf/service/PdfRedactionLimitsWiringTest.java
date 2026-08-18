// Verifies every PdfServiceProperties resource limit actually reaches the raster engine.
// Uses the autowired constructor, which builds RedactionLimits from seven positional
// arguments: a transposed pair there would silently apply the wrong ceiling in production.
package com.susswein.owlmask.pdf.service;

import com.susswein.owlmask.pdf.config.PdfServiceProperties;
import com.susswein.owlmask.share.pdf.PdfHash;
import com.susswein.owlmask.share.pdf.PdfRedactionException;
import com.susswein.owlmask.share.pdf.RedactSpec;
import com.susswein.owlmask.share.pdf.RedactionMode;
import com.susswein.owlmask.share.pdf.RedactionRegion;
import com.susswein.owlmask.share.pdf.RedactionSource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfRedactionLimitsWiringTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";
    private static final int DPI = 96;

    /** Every limit generous; individual tests tighten exactly one. */
    private static Limits generous() {
        return new Limits(10_000_000L, 50, 72, 300, 50_000_000L, 50_000_000L, 100);
    }

    private record Limits(long maxInputBytes, int maxPages, int minDpi, int maxDpi,
            long maxRenderedPixels, long maxDecodedStreamBytes, int maxRegions) {

        PdfServiceProperties properties() {
            return new PdfServiceProperties(TOKEN, maxInputBytes, maxPages, minDpi, maxDpi,
                    maxRenderedPixels, maxDecodedStreamBytes, maxRegions, 30, 2);
        }
    }

    /**
     * Runs a redaction through the autowired constructor and returns the fail-closed message.
     * Fails the test if the tightened limit was not enforced at all.
     */
    private static String messageFor(Limits limits, byte[] pdf, List<RedactionRegion> regions, int dpi) {
        PdfRedactionServiceImpl service = new PdfRedactionServiceImpl(limits.properties());
        RedactSpec spec = new RedactSpec(PdfHash.sha256(pdf), RedactionMode.RASTER_ALL, dpi, regions);
        PdfRedactionException exception =
                assertThrows(PdfRedactionException.class, () -> service.redact(pdf, spec));
        return String.valueOf(exception.getMessage());
    }

    /** Asserts the tightened limit still admits input that should be inside it. */
    private static void assertRedactionSucceeds(Limits limits, byte[] pdf,
            List<RedactionRegion> regions, int dpi) {
        PdfRedactionServiceImpl service = new PdfRedactionServiceImpl(limits.properties());
        RedactSpec spec = new RedactSpec(PdfHash.sha256(pdf), RedactionMode.RASTER_ALL, dpi, regions);

        assertTrue(service.redact(pdf, spec).verified(),
                "expected DPI " + dpi + " to be inside the configured range");
    }

    @Test
    void baselineLimitsAllowRedactionSoEachRejectionBelowIsAttributable() throws Exception {
        PdfRedactionServiceImpl service = new PdfRedactionServiceImpl(generous().properties());
        byte[] pdf = pdf(1);
        RedactSpec spec = new RedactSpec(PdfHash.sha256(pdf), RedactionMode.RASTER_ALL, DPI,
                List.of(region(0)));

        assertTrue(service.redact(pdf, spec).verified());
    }

    @Test
    void maxInputBytesIsWired() throws Exception {
        byte[] pdf = pdf(1);
        Limits limits = new Limits(pdf.length - 1L, 50, 72, 300, 50_000_000L, 50_000_000L, 100);

        assertTrue(messageFor(limits, pdf, List.of(region(0)), DPI).contains("maximum input size"));
    }

    @Test
    void maxPagesIsWired() throws Exception {
        Limits limits = new Limits(10_000_000L, 1, 72, 300, 50_000_000L, 50_000_000L, 100);

        assertTrue(messageFor(limits, pdf(2), List.of(region(0)), DPI).contains("page count"));
    }

    @Test
    void minDpiIsWiredAndNotSwappedWithMaxDpi() throws Exception {
        // minDpi and maxDpi are adjacent int arguments, so rejecting an out-of-range DPI
        // proves nothing on its own: a swapped pair rejects everything. The paired
        // acceptance above the floor is what pins this argument to the floor specifically.
        Limits limits = new Limits(10_000_000L, 50, 120, 300, 50_000_000L, 50_000_000L, 100);
        byte[] pdf = pdf(1);

        assertTrue(messageFor(limits, pdf, List.of(region(0)), 96).contains("DPI"));
        assertRedactionSucceeds(limits, pdf, List.of(region(0)), 200);
    }

    @Test
    void maxDpiIsWiredAndNotSwappedWithMinDpi() throws Exception {
        Limits limits = new Limits(10_000_000L, 50, 72, 120, 50_000_000L, 50_000_000L, 100);
        byte[] pdf = pdf(1);

        assertTrue(messageFor(limits, pdf, List.of(region(0)), 150).contains("DPI"));
        assertRedactionSucceeds(limits, pdf, List.of(region(0)), 96);
    }

    @Test
    void maxRenderedPixelsIsWiredAndNotSwappedWithDecodedStreamBytes() throws Exception {
        // Both are adjacent long arguments; the distinct message is what proves the order.
        Limits limits = new Limits(10_000_000L, 50, 72, 300, 1_000L, 50_000_000L, 100);

        assertTrue(messageFor(limits, pdf(1), List.of(region(0)), DPI).contains("rendered pixel budget"));
    }

    @Test
    void maxDecodedStreamBytesIsWiredAndNotSwappedWithRenderedPixels() throws Exception {
        Limits limits = new Limits(10_000_000L, 50, 72, 300, 50_000_000L, 10L, 100);

        assertTrue(messageFor(limits, pdf(1), List.of(region(0)), DPI).contains("decoded stream budget"));
    }

    @Test
    void maxRegionsIsWired() throws Exception {
        Limits limits = new Limits(10_000_000L, 50, 72, 300, 50_000_000L, 50_000_000L, 1);

        assertTrue(messageFor(limits, pdf(1), List.of(region(0), region(0)), DPI)
                .contains("region count"));
    }

    private static RedactionRegion region(int pageIndex) {
        return new RedactionRegion(pageIndex, List.of(0.1, 0.1, 0.6, 0.2),
                RedactionRegion.COORD_SPACE_V1, 612, 792, 0,
                List.of(0.0, 0.0, 612.0, 792.0), RedactionSource.TEXT);
    }

    private static byte[] pdf(int pageCount) throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (int index = 0; index < pageCount; index++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 16);
                    content.newLineAtOffset(100, 650);
                    content.showText("Patient SSN 123-45-6789 page " + index);
                    content.endText();
                }
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        }
    }
}
