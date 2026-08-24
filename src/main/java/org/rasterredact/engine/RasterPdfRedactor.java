// Reconstructs an input PDF as image-only pages and destroys requested pixel regions.
// Depends on PDFBox rendering plus structural and pixel-level verification helpers.
package org.rasterredact.engine;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public final class RasterPdfRedactor implements PdfRedactor {

    private final RedactionLimits limits;
    private final PdfStructureGuard structureGuard;
    private final RedactionCoordinateMapper coordinateMapper;
    private final RasterOutputVerifier outputVerifier;

    public RasterPdfRedactor() {
        this(RedactionLimits.secureDefaults());
    }

    public RasterPdfRedactor(RedactionLimits limits) {
        this.limits = limits;
        this.structureGuard = new PdfStructureGuard();
        this.coordinateMapper = new RedactionCoordinateMapper();
        this.outputVerifier = new RasterOutputVerifier();
    }

    @Override
    public RedactionResult redact(byte[] pdfBytes, RedactSpec spec) {
        validateRequest(pdfBytes, spec);
        try (PDDocument source = Loader.loadPDF(pdfBytes)) {
            structureGuard.validate(pdfBytes, source, spec.dpi(), limits);
            validateRegions(spec, source.getNumberOfPages());
            byte[] output = reconstruct(source, spec);
            outputVerifier.verify(output, spec);
            return new RedactionResult(output, true, source.getNumberOfPages(), spec.regions().size());
        } catch (PdfRedactionException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new PdfRedactionException("PDF redaction failed closed", exception);
        }
    }

    private void validateRequest(byte[] pdfBytes, RedactSpec spec) {
        if (pdfBytes == null || pdfBytes.length == 0 || spec == null) {
            throw new PdfRedactionException("PDF bytes and redaction spec are required");
        }
        String expected = spec.documentSha256() == null ? "" : spec.documentSha256().toLowerCase(Locale.ROOT);
        if (!PdfHash.sha256(pdfBytes).equals(expected)) {
            throw new PdfRedactionException("documentSha256 does not match the uploaded PDF");
        }
        if (spec.effectiveMode() != RedactionMode.RASTER_ALL) {
            throw new PdfRedactionException("RASTER_AFFECTED is not available in the v1 redactor");
        }
        if (spec.regions() == null) {
            throw new PdfRedactionException("Redaction regions are required");
        }
        if (spec.regions().size() > limits.maxRegions()) {
            throw new PdfRedactionException("Redaction region count exceeds the configured limit");
        }
    }

    private void validateRegions(RedactSpec spec, int pageCount) {
        for (RedactionRegion region : spec.regions()) {
            if (region == null || region.pageIndex() < 0 || region.pageIndex() >= pageCount) {
                throw new PdfRedactionException("Redaction region references an invalid page index");
            }
        }
    }

    private byte[] reconstruct(PDDocument source, RedactSpec spec) throws IOException {
        PDFRenderer renderer = new PDFRenderer(source);
        try (PDDocument output = new PDDocument()) {
            output.setDocumentInformation(new PDDocumentInformation());
            output.getDocumentCatalog().setMetadata(null);
            for (int pageIndex = 0; pageIndex < source.getNumberOfPages(); pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, spec.dpi(), ImageType.RGB);
                PDPage sourcePage = source.getPage(pageIndex);
                applyRegions(image, sourcePage, pageIndex, spec.regions());
                addImagePage(output, image, sourcePage);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            output.save(bytes);
            return bytes.toByteArray();
        }
    }

    private void applyRegions(
            BufferedImage image, PDPage page, int pageIndex, List<RedactionRegion> regions) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            for (RedactionRegion region : regions) {
                if (region.pageIndex() != pageIndex) {
                    continue;
                }
                PixelRectangle rectangle = coordinateMapper.map(region, page, image);
                graphics.fillRect(rectangle.x(), rectangle.y(), rectangle.width(), rectangle.height());
            }
        } finally {
            graphics.dispose();
        }
    }

    private void addImagePage(PDDocument output, BufferedImage image, PDPage sourcePage) throws IOException {
        PDRectangle crop = sourcePage.getCropBox();
        int rotation = Math.floorMod(sourcePage.getRotation(), 360);
        float displayedWidth = rotation == 90 || rotation == 270 ? crop.getHeight() : crop.getWidth();
        float displayedHeight = rotation == 90 || rotation == 270 ? crop.getWidth() : crop.getHeight();
        PDRectangle pageBox = new PDRectangle(displayedWidth, displayedHeight);
        PDPage page = new PDPage(pageBox);
        output.addPage(page);
        PDImageXObject pageImage = LosslessFactory.createFromImage(output, image);
        try (PDPageContentStream content = new PDPageContentStream(output, page)) {
            content.drawImage(pageImage, 0, 0, pageBox.getWidth(), pageBox.getHeight());
        }
    }
}
