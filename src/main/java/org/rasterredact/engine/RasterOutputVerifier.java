// Verifies reconstructed PDFs are text-free, structurally clean, and visibly blacked out.
// Uses PDFBox extraction, catalog inspection, and pixel sampling on saved output.
package org.rasterredact.engine;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

final class RasterOutputVerifier {

    void verify(byte[] output, RedactSpec spec) throws IOException {
        try (PDDocument document = Loader.loadPDF(output)) {
            if (!new PDFTextStripper().getText(document).isBlank()) {
                throw new PdfRedactionException("Raster output unexpectedly contains a text layer");
            }
            if (document.getDocumentCatalog().getAcroForm(null) != null
                    || document.getDocumentCatalog().getDocumentOutline() != null
                    || document.getDocumentCatalog().getOpenAction() != null
                    || document.getDocumentCatalog().getStructureTreeRoot() != null
                    || document.getDocumentCatalog().getMetadata() != null
                    || document.getDocumentCatalog().getNames() != null) {
                throw new PdfRedactionException("Raster output retained disallowed document structures");
            }
            verifyBlackRegions(document, spec);
        }
    }

    private void verifyBlackRegions(PDDocument document, RedactSpec spec) throws IOException {
        PDFRenderer renderer = new PDFRenderer(document);
        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            int currentPageIndex = pageIndex;
            List<RedactionRegion> pageRegions = spec.regions().stream()
                    .filter(region -> region.pageIndex() == currentPageIndex)
                    .toList();
            if (pageRegions.isEmpty()) {
                continue;
            }
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, spec.dpi(), ImageType.RGB);
            for (RedactionRegion region : pageRegions) {
                PixelRectangle rectangle = normalizedRectangle(region.bbox(), image);
                assertBlack(image, rectangle);
            }
        }
    }

    private PixelRectangle normalizedRectangle(List<Double> box, BufferedImage image) {
        int x0 = Math.max(0, (int) Math.floor(box.get(0) * image.getWidth()));
        int y0 = Math.max(0, (int) Math.floor(box.get(1) * image.getHeight()));
        int x1 = Math.min(image.getWidth(), (int) Math.ceil(box.get(2) * image.getWidth()));
        int y1 = Math.min(image.getHeight(), (int) Math.ceil(box.get(3) * image.getHeight()));
        return new PixelRectangle(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
    }

    private void assertBlack(BufferedImage image, PixelRectangle rectangle) {
        long samples = 0;
        long black = 0;
        int stride = Math.max(1, Math.min(rectangle.width(), rectangle.height()) / 25);
        for (int y = rectangle.y(); y < rectangle.y() + rectangle.height(); y += stride) {
            for (int x = rectangle.x(); x < rectangle.x() + rectangle.width(); x += stride) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                samples++;
                if (red <= 5 && green <= 5 && blue <= 5) {
                    black++;
                }
            }
        }
        if (samples == 0 || black * 100 < samples * 99) {
            throw new PdfRedactionException("A requested region was not blacked out in the saved PDF");
        }
    }
}
