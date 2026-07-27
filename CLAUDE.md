# OwlMask PDF Instructions

The workspace-wide product and engineering instructions are in
[`../CLAUDE.md`](../CLAUDE.md). This file adds rules specific to `owlmask-pdf`.

## Scope

`owlmask-pdf` is the private **raster-first** PDF redaction service (Java/Spring,
Apache PDFBox). `owlmask-sdk` detects PII in a PDF locally and calls this service
over HTTP with the rectangles to black out; this service renders each page,
blacks the pixels, and rebuilds a fresh image-only PDF.

**Why raster-first:** it destroys the text layer and every document-level
structure, so a "redacted" region cannot be recovered by selecting text, reading
an annotation, or inspecting an object stream — the failure mode of every
overlay-based redactor.

**Why a separate service:** it keeps AGPL `pymupdf` out of the system. Redaction
uses PDFBox (Apache-2.0). Do not add an AGPL dependency here or in `owlmask-sdk`.

## Technology

Derive versions from `pom.xml`. As of 2026-07-26: Java 21, Spring Boot 3.5.14,
PDFBox 3.x via `owlmask-share-pdf`.

## Rules that are easy to get wrong

- **A failed verification is a terminal failure, not a best effort.** If the
  output cannot be verified, fail the request — never return a partially
  redacted PDF.
- **Auth is already fail-closed and deliberately does not use Spring Security.**
  `BearerTokenFilter` requires the token on every path except `/health`, returns
  503 when no token is configured, and compares in constant time. Its default
  branch is *require*, not *skip*, so the "custom filters have no fail-closed
  default" critique that justified Spring Security in `owlmask-code` does not
  apply. Adding a security chain here is churn with regression risk.
- **Credential handling comes from `owlmask-share-security`** (`ApiCredentials`),
  not a local copy — one implementation, one review.
- **Depend on `owlmask-share-security`, never `owlmask-share`.** The latter
  carries langchain4j, retrofit, kotlin-stdlib and okhttp at compile scope; this
  service must not inherit an LLM stack to redact a PDF.
- **`PdfServiceProperties` rejects a token shorter than 32 characters at
  construction**, so a weak token fails startup rather than production.
- **Never log PDF content, extracted text, or detected values.** Hashes, counts
  and correlation ids only.

## Verification

```bash
mvn test
```

Redaction changes need a test that asserts the *output* has no recoverable text
layer, not merely that the call returned 200.
