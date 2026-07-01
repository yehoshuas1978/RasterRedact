// Runs raster PDF redaction with bounded concurrency and a request wall-clock timeout.
// Delegates resource validation and reconstruction to RasterPdfRedactor.
package com.susswein.owlmask.pdf.service;

import com.susswein.owlmask.pdf.config.PdfServiceProperties;
import com.susswein.owlmask.share.pdf.PdfRedactionException;
import com.susswein.owlmask.share.pdf.RasterPdfRedactor;
import com.susswein.owlmask.share.pdf.RedactSpec;
import com.susswein.owlmask.share.pdf.RedactionLimits;
import com.susswein.owlmask.share.pdf.RedactionResult;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class PdfRedactionServiceImpl implements PdfRedactionService {

    private final RasterPdfRedactor redactor;
    private final PdfServiceProperties properties;
    private final ThreadPoolExecutor executor;

    public PdfRedactionServiceImpl(PdfServiceProperties properties) {
        this.properties = properties;
        this.redactor = new RasterPdfRedactor(new RedactionLimits(
                properties.maxInputBytes(), properties.maxPages(), properties.minDpi(), properties.maxDpi(),
                properties.maxRenderedPixels(), properties.maxDecodedStreamBytes(), properties.maxRegions()));
        int concurrency = Math.max(1, properties.maxConcurrency());
        this.executor = new ThreadPoolExecutor(concurrency, concurrency, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(concurrency), new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public RedactionResult redact(byte[] pdfBytes, RedactSpec spec) {
        Future<RedactionResult> future;
        try {
            future = executor.submit(() -> redactor.redact(pdfBytes, spec));
        } catch (RuntimeException exception) {
            throw new PdfRedactionException("PDF redaction capacity is exhausted", exception);
        }
        try {
            return future.get(Math.max(1, properties.timeoutSeconds()), TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new PdfRedactionException("PDF redaction exceeded the wall-clock timeout", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PdfRedactionException("PDF redaction was interrupted", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof PdfRedactionException redactionException) {
                throw redactionException;
            }
            throw new PdfRedactionException("PDF redaction failed closed", exception.getCause());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
