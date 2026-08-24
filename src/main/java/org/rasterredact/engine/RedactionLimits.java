// Defines bounded resource consumption for parsing and rendering hostile PDF input.
// Applied by RasterPdfRedactor before any output is returned.
package org.rasterredact.engine;

public record RedactionLimits(
        long maxInputBytes,
        int maxPages,
        int minDpi,
        int maxDpi,
        long maxRenderedPixels,
        long maxDecodedStreamBytes,
        int maxRegions
) {
    public static RedactionLimits secureDefaults() {
        return new RedactionLimits(25L * 1024 * 1024, 200, 72, 300,
                250_000_000L, 512L * 1024 * 1024, 10_000);
    }
}
