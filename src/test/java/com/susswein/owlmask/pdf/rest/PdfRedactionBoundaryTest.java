// Verifies the HTTP request boundary: the input-size guard rejects before any PDF work,
// malformed multipart is a client error, the bearer filter is actually in the chain, and
// no error response carries PDF bytes. The redaction service is mocked so that "the guard
// ran first" is observable rather than inferred.
package com.susswein.owlmask.pdf.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.susswein.owlmask.pdf.service.PdfRedactionService;
import com.susswein.owlmask.share.pdf.PdfRedactionException;
import com.susswein.owlmask.share.pdf.RedactSpec;
import com.susswein.owlmask.share.pdf.RedactionMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "owlmask.pdf.auth-token=test-secret-that-is-at-least-32-chars",
        // Deliberately far below the servlet multipart ceiling so oversized uploads reach
        // the controller guard instead of being rejected by Tomcat first.
        "owlmask.pdf.max-input-bytes=4096",
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
class PdfRedactionBoundaryTest {

    private static final String BEARER = "Bearer test-secret-that-is-at-least-32-chars";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PdfRedactionService redactionService;

    private MockMultipartFile specPart() throws Exception {
        RedactSpec spec = new RedactSpec("0".repeat(64), RedactionMode.RASTER_ALL, 96, List.of());
        return new MockMultipartFile("spec", "", "application/json", objectMapper.writeValueAsBytes(spec));
    }

    @Test
    void emptyUploadIsRejectedWithoutInvokingRedaction() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "input.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/pdf/redact").file(empty).file(specPart()).header("Authorization", BEARER))
                .andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(redactionService);
    }

    @Test
    void oversizedUploadIsRejectedWithoutInvokingRedaction() throws Exception {
        // 8 KiB against the 4 KiB configured ceiling.
        byte[] oversized = new byte[8192];
        MockMultipartFile file = new MockMultipartFile("file", "input.pdf", "application/pdf", oversized);

        mockMvc.perform(multipart("/pdf/redact").file(file).file(specPart()).header("Authorization", BEARER))
                .andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(redactionService);
    }

    @Test
    void uploadExactlyAtTheCeilingIsNotRejectedByTheSizeGuard() throws Exception {
        // Guards the boundary condition: the check is `>`, so 4096 bytes must pass the
        // guard and reach the service rather than being turned away off-by-one.
        byte[] atLimit = new byte[4096];
        MockMultipartFile file = new MockMultipartFile("file", "input.pdf", "application/pdf", atLimit);
        when(redactionService.redact(any(), any()))
                .thenThrow(new PdfRedactionException("reached the redaction service"));

        mockMvc.perform(multipart("/pdf/redact").file(file).file(specPart()).header("Authorization", BEARER))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("reached the redaction service"));
    }

    @Test
    void missingSpecPartIsAClientErrorNotAServerError() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "input.pdf", "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/pdf/redact").file(file).header("Authorization", BEARER))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(redactionService);
    }

    @Test
    void missingFilePartIsAClientErrorNotAServerError() throws Exception {
        mockMvc.perform(multipart("/pdf/redact").file(specPart()).header("Authorization", BEARER))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(redactionService);
    }

    @Test
    void bearerFilterIsRegisteredInTheChainAheadOfRedaction() throws Exception {
        // The unit test proves the filter's logic; this proves the container actually
        // applies it to /pdf/redact, which a lost @Component registration would break.
        MockMultipartFile file = new MockMultipartFile("file", "input.pdf", "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/pdf/redact").file(file).file(specPart())
                        .header("Authorization", "Bearer wrong-token-that-is-at-least-32-chars"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(redactionService);
    }

    @Test
    void failClosedErrorBodyCarriesNoPdfBytes() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "input.pdf", "application/pdf",
                "%PDF-1.7 Patient SSN 123-45-6789".getBytes(StandardCharsets.UTF_8));
        when(redactionService.redact(any(), any()))
                .thenThrow(new PdfRedactionException("documentSha256 does not match the uploaded PDF"));

        MvcResult result = mockMvc.perform(multipart("/pdf/redact").file(file).file(specPart())
                        .header("Authorization", BEARER))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("%PDF"), "error body echoed PDF payload: " + body);
        assertFalse(body.contains("123-45-6789"), "error body echoed document content: " + body);
    }

    @Test
    void healthReportsTheDocumentedLivenessContractWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.service").value("owlmask-pdf"));
    }
}
