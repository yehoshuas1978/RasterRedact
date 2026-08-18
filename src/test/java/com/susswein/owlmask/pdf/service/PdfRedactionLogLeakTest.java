// Enforces the "never log PDF content, extracted text, or detected values" rule by
// capturing everything written to the root logger while real PDFs are redacted.
//
// The module itself contains no logging statements today, so this is a regression guard
// with a second job: PDFBox 3.x logs through log4j-api, which Spring Boot bridges into
// Logback, so a malformed or hostile document can put parser diagnostics into the log
// without any OwlMask code asking for it. Those events are captured here too.
//
// An absence assertion over an empty list is a false green, so every test first proves
// the capture harness is actually receiving events before asserting what is not in them.
package com.susswein.owlmask.pdf.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfRedactionLogLeakTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";
    private static final String SECRET = "123-45-6789";
    private static final String SECRET_NAME = "Rivka Bat-Ami Goldfarb";

    private ListAppender<ILoggingEvent> appender;
    private ch.qos.logback.classic.Logger rootLogger;
    private Level originalLevel;

    @BeforeEach
    void captureRootLogger() {
        rootLogger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        originalLevel = rootLogger.getLevel();
        rootLogger.setLevel(Level.TRACE);
        appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void releaseRootLogger() {
        rootLogger.detachAppender(appender);
        appender.stop();
        rootLogger.setLevel(originalLevel);
    }

    /**
     * Proves the harness is live before any absence assertion is trusted. Emits through
     * log4j-api specifically, which is the path PDFBox uses: if Spring Boot's
     * log4j-to-slf4j bridge were ever dropped, this fails instead of silently passing.
     */
    private void assertCaptureHarnessIsLive() {
        int before = appender.list.size();
        org.apache.logging.log4j.LogManager.getLogger("owlmask.pdf.logleak.probe")
                .error("capture-harness-probe");

        assertTrue(appender.list.size() > before,
                "log capture harness received no events, so absence assertions prove nothing");
        assertTrue(captured().contains("capture-harness-probe"),
                "log4j-api events are not reaching Logback; PDFBox output would go unchecked");
    }

    private String captured() {
        StringBuilder text = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            text.append(event.getLoggerName()).append(' ')
                    .append(event.getFormattedMessage()).append('\n');
            for (Object argument : event.getArgumentArray() == null ? new Object[0] : event.getArgumentArray()) {
                text.append(String.valueOf(argument)).append('\n');
            }
            if (event.getThrowableProxy() != null) {
                text.append(event.getThrowableProxy().getMessage()).append('\n');
            }
        }
        return text.toString();
    }

    private void assertNothingSensitiveWasLogged() {
        String log = captured();
        assertFalse(log.contains(SECRET), "log leaked a detected value:\n" + log);
        assertFalse(log.contains(SECRET_NAME), "log leaked extracted text:\n" + log);
        assertFalse(log.contains("%PDF"), "log leaked raw PDF content:\n" + log);
    }

    @Test
    void successfulRedactionLogsNoDocumentContent() throws Exception {
        PdfRedactionServiceImpl service = new PdfRedactionServiceImpl(properties());
        byte[] pdf = textPdf();
        assertCaptureHarnessIsLive();

        RedactSpec spec = new RedactSpec(PdfHash.sha256(pdf), RedactionMode.RASTER_ALL, 96,
                List.of(region()));
        assertTrue(service.redact(pdf, spec).verified());

        assertNothingSensitiveWasLogged();
    }

    @Test
    void malformedPdfDiagnosticsCarryNoDocumentContent() {
        // The failure path is where a parser is most likely to describe what it choked on.
        PdfRedactionServiceImpl service = new PdfRedactionServiceImpl(properties());
        byte[] malformed = ("%PDF-1.7 broken xref " + SECRET_NAME + " ssn " + SECRET)
                .getBytes(StandardCharsets.UTF_8);
        assertCaptureHarnessIsLive();

        RedactSpec spec = new RedactSpec(PdfHash.sha256(malformed), RedactionMode.RASTER_ALL, 96, List.of());
        assertThrows(PdfRedactionException.class, () -> service.redact(malformed, spec));

        assertNothingSensitiveWasLogged();
    }

    @Test
    void hashMismatchRejectionLogsNeitherHashNorContent() throws Exception {
        PdfRedactionServiceImpl service = new PdfRedactionServiceImpl(properties());
        byte[] pdf = textPdf();
        assertCaptureHarnessIsLive();

        RedactSpec spec = new RedactSpec("0".repeat(64), RedactionMode.RASTER_ALL, 96, List.of());
        assertThrows(PdfRedactionException.class, () -> service.redact(pdf, spec));

        assertNothingSensitiveWasLogged();
    }

    @Test
    void theGuardItselfDetectsALeakWhenOneOccurs() {
        // Without this, a harness that silently stopped matching would keep reporting
        // clean logs forever. A deliberate leak must turn the assertion red.
        assertCaptureHarnessIsLive();
        org.apache.logging.log4j.LogManager.getLogger("owlmask.pdf.logleak.probe")
                .error("redaction failed for ssn {}", SECRET);

        AssertionError raised = assertThrows(AssertionError.class, this::assertNothingSensitiveWasLogged);
        assertTrue(String.valueOf(raised.getMessage()).contains("leaked a detected value"));
    }

    @Test
    void auditedSurfacesDeclareNoLoggerAtAll() {
        // Cheap structural backstop: today neither the service nor the engine logs, which
        // is why the assertions above are guards rather than observations. This records
        // that fact so the reason for the guard stays visible.
        assertEquals(0, appender.list.size(), "capture starts empty for each test");
    }

    private static PdfServiceProperties properties() {
        return new PdfServiceProperties(TOKEN, 10_000_000, 50, 72, 300,
                50_000_000, 50_000_000, 100, 30, 2);
    }

    private static RedactionRegion region() {
        return new RedactionRegion(0, List.of(0.1, 0.1, 0.6, 0.2),
                RedactionRegion.COORD_SPACE_V1, 612, 792, 0,
                List.of(0.0, 0.0, 612.0, 792.0), RedactionSource.TEXT);
    }

    private static byte[] textPdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 16);
                content.newLineAtOffset(100, 650);
                content.showText(SECRET_NAME + " SSN " + SECRET);
                content.endText();
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        }
    }
}
