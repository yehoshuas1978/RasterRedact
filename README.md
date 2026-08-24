# RasterRedact

> **Open Source PDF Redaction** — Safely and securely flatten and redact PDFs. See [LICENSE](./LICENSE) for MIT License details.

RasterRedact is a **raster-first PDF redaction** microservice.
It takes an uploaded PDF plus a set of hash-bound, normalized rectangles and
returns a **verified image-only PDF** in which the requested regions are
destroyed at the pixel level — real redaction, not a black box drawn over text
that can still be copied out.

## Why Raster-First?
Standard PDF redaction (removing text objects from the content stream) is notoriously prone to leakage through hidden layers, metadata, and structural artifacts. RasterRedact sidesteps this completely by rendering the document to images and redacting the pixels themselves.

## License
MIT License
