// Maps normalized top-left PDF detection rectangles into rendered image pixels.
// Validates coordinate versions, page geometry, rotation, and crop metadata.
package org.rasterredact.engine;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.awt.image.BufferedImage;
import java.util.List;

final class RedactionCoordinateMapper {

    private static final double PAGE_TOLERANCE_PT = 2.0;
    private static final int SAFETY_MARGIN_PIXELS = 2;

    PixelRectangle map(RedactionRegion region, PDPage page, BufferedImage image) {
        validateRegion(region);
        validatePageGeometry(region, page);
        List<Double> box = region.bbox();
        int x0 = Math.max(0, (int) Math.floor(box.get(0) * image.getWidth()) - SAFETY_MARGIN_PIXELS);
        int y0 = Math.max(0, (int) Math.floor(box.get(1) * image.getHeight()) - SAFETY_MARGIN_PIXELS);
        int x1 = Math.min(image.getWidth(),
                (int) Math.ceil(box.get(2) * image.getWidth()) + SAFETY_MARGIN_PIXELS);
        int y1 = Math.min(image.getHeight(),
                (int) Math.ceil(box.get(3) * image.getHeight()) + SAFETY_MARGIN_PIXELS);
        return new PixelRectangle(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
    }

    private void validateRegion(RedactionRegion region) {
        if (region == null || !RedactionRegion.COORD_SPACE_V1.equals(region.coordSpaceVersion())) {
            throw new PdfRedactionException("Unsupported or missing redaction coordinate space");
        }
        if (region.bbox() == null || region.bbox().size() != 4) {
            throw new PdfRedactionException("Redaction bbox must contain four normalized coordinates");
        }
        for (Double coordinate : region.bbox()) {
            if (coordinate == null || !Double.isFinite(coordinate) || coordinate < 0 || coordinate > 1) {
                throw new PdfRedactionException("Redaction bbox coordinates must be finite values in 0..1");
            }
        }
        if (region.bbox().get(0) >= region.bbox().get(2) || region.bbox().get(1) >= region.bbox().get(3)) {
            throw new PdfRedactionException("Redaction bbox must have positive width and height");
        }
        if (region.source() == null) {
            throw new PdfRedactionException("Redaction source is required");
        }
        if (region.rotation() != 0 && region.rotation() != 90
                && region.rotation() != 180 && region.rotation() != 270) {
            throw new PdfRedactionException("Page rotation must be 0, 90, 180, or 270");
        }
        if (!Double.isFinite(region.pageWidthPt()) || !Double.isFinite(region.pageHeightPt())
                || region.pageWidthPt() <= 0 || region.pageHeightPt() <= 0) {
            throw new PdfRedactionException("Page dimensions must be finite positive values");
        }
        if (region.cropBox() == null || region.cropBox().size() != 4) {
            throw new PdfRedactionException("A four-coordinate crop box is required");
        }
        for (Double coordinate : region.cropBox()) {
            if (coordinate == null || !Double.isFinite(coordinate)) {
                throw new PdfRedactionException("Crop box coordinates must be finite values");
            }
        }
        if (region.cropBox().get(0) >= region.cropBox().get(2)
                || region.cropBox().get(1) >= region.cropBox().get(3)) {
            throw new PdfRedactionException("Crop box must have positive width and height");
        }
    }

    private void validatePageGeometry(RedactionRegion region, PDPage page) {
        PDRectangle crop = page.getCropBox();
        int actualRotation = Math.floorMod(page.getRotation(), 360);
        double displayedWidth = actualRotation == 90 || actualRotation == 270 ? crop.getHeight() : crop.getWidth();
        double displayedHeight = actualRotation == 90 || actualRotation == 270 ? crop.getWidth() : crop.getHeight();
        if (actualRotation != region.rotation()
                || Math.abs(displayedWidth - region.pageWidthPt()) > PAGE_TOLERANCE_PT
                || Math.abs(displayedHeight - region.pageHeightPt()) > PAGE_TOLERANCE_PT) {
            throw new PdfRedactionException("Redaction page geometry does not match the supplied PDF");
        }
        double[] actual = {crop.getLowerLeftX(), crop.getLowerLeftY(), crop.getUpperRightX(), crop.getUpperRightY()};
        for (int index = 0; index < actual.length; index++) {
            if (Math.abs(actual[index] - region.cropBox().get(index)) > PAGE_TOLERANCE_PT) {
                throw new PdfRedactionException("Redaction crop box does not match the supplied PDF");
            }
        }
    }
}
