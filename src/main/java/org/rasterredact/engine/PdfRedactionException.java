// Signals a fail-closed PDF validation, resource-limit, rendering, or verification failure.
// Used by both the shared engine and the raster-redact HTTP boundary.
package org.rasterredact.engine;

public class PdfRedactionException extends RuntimeException {

    public PdfRedactionException(String message) {
        super(message);
    }

    public PdfRedactionException(String message, Throwable cause) {
        super(message, cause);
    }
}
