// Verifies terminal-failure semantics and end-to-end raster redaction of the bounded service.
// Builds deterministic fixture PDFs (text-layer, malformed, encrypted) with PDFBox.
package com.susswein.owlmask.pdf.service;

import com.susswein.owlmask.pdf.config.PdfServiceProperties;
import com.susswein.owlmask.share.pdf.PdfHash;
import com.susswein.owlmask.share.pdf.PdfRedactionException;
import com.susswein.owlmask.share.pdf.PdfRedactor;
import com.susswein.owlmask.share.pdf.RedactSpec;
import com.susswein.owlmask.share.pdf.RedactionMode;
import com.susswein.owlmask.share.pdf.RedactionRegion;
import com.susswein.owlmask.share.pdf.RedactionResult;
import com.susswein.owlmask.share.pdf.RedactionSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfRedactionServiceImplTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    @Test
    void redactsTextLayerPdfIntoVerifiedImageOnlyOutput() throws Exception {
        PdfRedactionServiceImpl service = new PdfRedactionServiceImpl(properties(30, 2));
        byte[] input = textPdf();
        RedactSpec spec = new RedactSpec(PdfHash.sha256(input), RedactionMode.RASTER_ALL, 96,
                List.of(region(0)));

        RedactionResult result = service.redact(input, spec);

        assertTrue(result.verified());
        assertEquals(1, result.pagesRasterized());
        assertEquals(1, result.regionsApplied());
        try (PDDocument output = Loader.loadPDF(result.pdfBytes())) {
            assertEquals(1, output.getNumberOfPages());
            assertTrue(new PDFTextStripper().getText(output).isBlank(),
                    "text layer must be destroyed in the rebuilt PDF");
            assertNull(output.getDocumentInformation().getAuthor(),
                    "document metadata must not survive reconstruction");
        }
    }

    @Test
    void malformedPdfIsTerminalFailure() {
        PdfRedactionServiceImpl service = new PdfRedactionServiceImpl(properties(30, 2));
        byte[] garbage = "%PDF-1.7 this is not a real pdf body".getBytes(StandardCharsets.UTF_8);
        RedactSpec spec = new RedactSpec(PdfHash.sha256(garbage), RedactionMode.RASTER_ALL, 96, List.of());

        assertThrows(PdfRedactionException.class, () -> service.redact(garbage, spec));
    }

    @Test
    void encryptedPdfIsTerminalFailure() throws Exception {
        PdfRedactionServiceImpl service = new PdfRedactionServiceImpl(properties(30, 2));
        byte[] encrypted = encryptedPdf();
        RedactSpec spec = new RedactSpec(PdfHash.sha256(encrypted), RedactionMode.RASTER_ALL, 96, List.of());

        assertThrows(PdfRedactionException.class, () -> service.redact(encrypted, spec));
    }

    @Test
    void hashMismatchIsTerminalFailure() throws Exception {
        PdfRedactionServiceImpl service = new PdfRedactionServiceImpl(properties(30, 2));
        byte[] input = textPdf();
        RedactSpec spec = new RedactSpec("0".repeat(64), RedactionMode.RASTER_ALL, 96, List.of());

        assertThrows(PdfRedactionException.class, () -> service.redact(input, spec));
    }

    @Test
    void wallClockTimeoutIsTerminalFailure() {
        CountDownLatch release = new CountDownLatch(1);
        PdfRedactor hangingRedactor = (bytes, spec) -> {
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            throw new PdfRedactionException("should have timed out before completing");
        };
        PdfRedactionServiceImpl service = new PdfRedactionServiceImpl(properties(1, 2), hangingRedactor);
        try {
            PdfRedactionException exception = assertThrows(PdfRedactionException.class,
                    () -> service.redact(new byte[] {1}, emptySpec()));
            assertTrue(exception.getMessage().contains("wall-clock timeout"), exception.getMessage());
        } finally {
            release.countDown();
        }
    }

    @Test
    void capacityExhaustionIsTerminalFailureAndQueuedWorkStillCompletes() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PdfRedactor blockingRedactor = (bytes, spec) -> {
            firstStarted.countDown();
            try {
                if (!release.await(30, TimeUnit.SECONDS)) {
                    throw new PdfRedactionException("test latch was never released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new PdfRedactionException("interrupted while waiting for the test latch");
            }
            return new RedactionResult(new byte[] {1}, true, 1, 0);
        };
        PdfRedactionServiceImpl service = new PdfRedactionServiceImpl(properties(30, 1), blockingRedactor);

        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        Thread running = redactInThread(service, workerFailure);
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS), "first redaction never started");
        Thread queued = redactInThread(service, workerFailure);
        awaitParked(queued);

        // Pool and queue (capacity 1 each) are now full; the next request must fail closed.
        assertThrows(PdfRedactionException.class, () -> service.redact(new byte[] {1}, emptySpec()));

        release.countDown();
        running.join(TimeUnit.SECONDS.toMillis(10));
        queued.join(TimeUnit.SECONDS.toMillis(10));
        assertNull(workerFailure.get(), String.valueOf(workerFailure.get()));
    }

    private static Thread redactInThread(PdfRedactionServiceImpl service, AtomicReference<Throwable> failure) {
        Thread thread = new Thread(() -> {
            try {
                service.redact(new byte[] {1}, emptySpec());
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        });
        thread.start();
        return thread;
    }

    private static void awaitParked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("queued redaction request never blocked on the executor");
    }

    private static RedactSpec emptySpec() {
        return new RedactSpec("unused", RedactionMode.RASTER_ALL, 96, List.of());
    }

    private static PdfServiceProperties properties(int timeoutSeconds, int maxConcurrency) {
        return new PdfServiceProperties(TOKEN, 10_000_000, 50, 72, 300,
                50_000_000, 50_000_000, 100, timeoutSeconds, maxConcurrency);
    }

    private static RedactionRegion region(int pageIndex) {
        return new RedactionRegion(pageIndex, List.of(0.1, 0.1, 0.6, 0.2),
                RedactionRegion.COORD_SPACE_V1, 612, 792, 0,
                List.of(0.0, 0.0, 612.0, 792.0), RedactionSource.TEXT);
    }

    private static byte[] textPdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            document.getDocumentInformation().setAuthor("Sensitive Author");
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 16);
                content.newLineAtOffset(100, 650);
                content.showText("Patient SSN 123-45-6789");
                content.endText();
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    private static byte[] encryptedPdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.LETTER));
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("owner-secret", "", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        }
    }
}
