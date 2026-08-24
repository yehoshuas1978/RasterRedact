// Defines a hash-bound request for raster-first PDF redaction.
// Consumed by PdfRedactor and serialized by the raster-redact HTTP service.
package org.rasterredact.engine;

import java.util.List;

public record RedactSpec(
        String documentSha256,
        RedactionMode mode,
        int dpi,
        List<RedactionRegion> regions
) {
    public RedactionMode effectiveMode() {
        return mode == null ? RedactionMode.RASTER_ALL : mode;
    }
}
