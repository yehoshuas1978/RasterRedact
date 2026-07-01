// Verifies health, bearer authentication, multipart contracts, and verified PDF headers.
// Runs the real raster engine through Spring MockMvc with a deterministic test PDF.
package com.susswein.owlmask.pdf.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.susswein.owlmask.share.pdf.PdfHash;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "owlmask.pdf.auth-token=test-secret-that-is-at-least-32-chars",
        "owlmask.pdf.max-input-bytes=10485760",
        "owlmask.pdf.max-pages=10",
        "owlmask.pdf.min-dpi=72",
        "owlmask.pdf.max-dpi=150",
        "owlmask.pdf.max-rendered-pixels=50000000",
        "owlmask.pdf.max-decoded-stream-bytes=50000000",
        "owlmask.pdf.max-regions=100",
        "owlmask.pdf.timeout-seconds=30",
        "owlmask.pdf.max-concurrency=1"
})
@AutoConfigureMockMvc
class PdfRedactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isOk());
    }

    @Test
    void redactionRequiresBearerToken() throws Exception {
        mockMvc.perform(multipart("/pdf/redact"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsVerifiedPdfAndMechanicalCounts() throws Exception {
        byte[] pdf = createPdf();
        RedactionRegion region = new RedactionRegion(0, List.of(0.1, 0.1, 0.6, 0.3),
                RedactionRegion.COORD_SPACE_V1, 612, 792, 0,
                List.of(0.0, 0.0, 612.0, 792.0), RedactionSource.TEXT);
        RedactSpec spec = new RedactSpec(PdfHash.sha256(pdf), RedactionMode.RASTER_ALL, 96, List.of(region));
        MockMultipartFile file = new MockMultipartFile("file", "input.pdf", "application/pdf", pdf);
        MockMultipartFile specPart = new MockMultipartFile("spec", "", "application/json",
                objectMapper.writeValueAsBytes(spec));

        mockMvc.perform(multipart("/pdf/redact").file(file).file(specPart)
                        .header("Authorization", "Bearer test-secret-that-is-at-least-32-chars")
                        .accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Redaction-Verified", "true"))
                .andExpect(header().string("X-Pages-Rasterized", "1"))
                .andExpect(header().string("X-Regions-Applied", "1"));
    }

    private byte[] createPdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                content.newLineAtOffset(72, 700);
                content.showText("Secret 123-45-6789");
                content.endText();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }
}
