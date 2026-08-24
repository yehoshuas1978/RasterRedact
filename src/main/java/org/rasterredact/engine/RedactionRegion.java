// Carries one normalized, versioned PDF redaction rectangle and page geometry.
// Coordinates use a top-left origin and bind to the detector's displayed page.
package org.rasterredact.engine;

import java.util.List;

public record RedactionRegion(
        int pageIndex,
        List<Double> bbox,
        String coordSpaceVersion,
        double pageWidthPt,
        double pageHeightPt,
        int rotation,
        List<Double> cropBox,
        RedactionSource source
) {
    public static final String COORD_SPACE_V1 = "v1-topleft-normalized";
}
