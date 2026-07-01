# owlmask-pdf

> **Proprietary — All Rights Reserved.** Private, closed-source service. NOT open
> source. See [LICENSE](./LICENSE).

A private **raster-first PDF redaction** microservice for the OwlMask platform.
It takes an uploaded PDF plus a set of hash-bound, normalized rectangles and
returns a **verified image-only PDF** in which the requested regions are
destroyed at the pixel level — real redaction, not a black box drawn over text
that can still be copied out.

- **Java 21 · Spring Boot 3.4.1 · Apache PDFBox** (Apache-2.0).
- **No AGPL anywhere.** This service replaces the retired AGPL `copyleft-sidecar`
  (which wrapped PyMuPDF). Because PDFBox is Apache-2.0, this code stays fully
  proprietary.

## Why raster-first?

Instead of surgically editing the PDF content stream, owlmask-pdf **renders each
page to an image, blacks out the region rectangles on the pixels, and rebuilds a
brand-new image-only PDF.** This is deliberately the safest approach:

- **Every content-stream type is covered.** Rendering composites page streams,
  Form XObjects, appearance streams, Type3 glyphs, and tiling patterns — there is
  no hidden text layer left behind.
- **Document-level leak vectors vanish by construction.** A freshly built
  image-only PDF carries **no** incremental-update revisions, XFA, JavaScript /
  actions, embedded/attached files, optional-content layers, outlines,
  thumbnails, structure tree, or document metadata.
- **The output is verified before it is returned** (see [Guarantees](#guarantees)).

**Trade-off:** the redacted output has **no selectable/searchable text layer**
(classic "flatten to image" redaction). That is the intended v1 posture — safety
over fidelity.

## Where it fits

owlmask-pdf does **not** detect PII. Detection stays in `owlmask-sdk`
(Presidio + pdfplumber + OCR). owlmask-sdk computes the regions and calls this
service only to perform the mechanical, verified redaction:

```
owlmask-sdk (Python)                         owlmask-pdf (this service)
  detect PII → normalized regions  ──HTTP──▶   render → black regions →
  + document SHA-256                           rebuild image-only PDF →
                                               verify → return verified PDF
```

## API

`POST /pdf/redact` requires `Authorization: Bearer <token>` (constant-time
comparison). The service refuses to start without a sufficiently long token.

### `GET /health`
Unauthenticated liveness probe.
```json
{ "status": "healthy", "service": "owlmask-pdf" }
```

### `POST /pdf/redact`
`multipart/form-data`, produces `application/pdf`.

| Part | Type | Description |
|---|---|---|
| `file` | binary | The original PDF |
| `spec` | JSON (`application/json`) | The redaction spec (below) |

**`spec` — `RedactSpec`:**

| Field | Type | Notes |
|---|---|---|
| `documentSha256` | string | SHA-256 (hex) of the exact `file` bytes. Rejected on mismatch — binds regions to this PDF. |
| `mode` | enum | `RASTER_ALL` (only supported mode in v1). |
| `dpi` | int | Render DPI (bounded by `min/max-dpi`). |
| `regions` | array | Rectangles to destroy (below). |

**`regions[]` — `RedactionRegion`:**

| Field | Type | Notes |
|---|---|---|
| `pageIndex` | int | 0-based page index. |
| `bbox` | `[x0,y0,x1,y1]` | **Normalized** to `0..1`, **top-left** origin. |
| `coordSpaceVersion` | string | `"v1-topleft-normalized"`. |
| `pageWidthPt` / `pageHeightPt` | number | Page size in points (from the detector). |
| `rotation` | int | `0` / `90` / `180` / `270`. |
| `cropBox` | `[x0,y0,x1,y1]` | Page crop box in points. |
| `source` | enum | `TEXT` or `OCR`. Image/OCR regions are always destroyed at the pixel level. |

**Response headers:**

| Header | Meaning |
|---|---|
| `X-Redaction-Verified` | `true` — output passed verification (always `true` on 200). |
| `X-Pages-Rasterized` | Pages reconstructed. |
| `X-Regions-Applied` | Regions blacked out. |

**Errors (fail closed — the original PDF is never returned in an error body):**

| Status | When |
|---|---|
| `401` | Missing/invalid bearer token. |
| `422` | Redaction failed closed (hash mismatch, limit breach, bad geometry, verification failure, …). |
| `400` | Malformed multipart / spec. |

### Example

```bash
SHA=$(sha256sum in.pdf | cut -d' ' -f1)
cat > spec.json <<JSON
{
  "documentSha256": "$SHA",
  "mode": "RASTER_ALL",
  "dpi": 150,
  "regions": [
    {
      "pageIndex": 0,
      "bbox": [0.10, 0.10, 0.55, 0.18],
      "coordSpaceVersion": "v1-topleft-normalized",
      "pageWidthPt": 612, "pageHeightPt": 792,
      "rotation": 0,
      "cropBox": [0, 0, 612, 792],
      "source": "TEXT"
    }
  ]
}
JSON

curl -sS -X POST http://127.0.0.1:9070/pdf/redact \
  -H "Authorization: Bearer $OWLMASK_PDF_AUTH_TOKEN" \
  -F "file=@in.pdf;type=application/pdf" \
  -F "spec=@spec.json;type=application/json" \
  -o redacted.pdf -D -
```

## Guarantees

Every returned PDF is verified before the service responds; verification failure
returns `422` with no document. The engine asserts:

- The output has **no extractable text layer** (`PDFTextStripper` is empty).
- **No** AcroForm, document outline, open action, structure tree, XMP/metadata, or
  names dictionary survived.
- Each requested region is **≥99% black** when the saved output is re-rendered
  (pixel sampling confirms that the requested rectangle survived save/reload).
- `documentSha256` matches the uploaded bytes.

## Configuration

All settings come from environment variables (see `src/main/resources/application.yaml`).

| Variable | Default | Description |
|---|---|---|
| `OWLMASK_PDF_HOST` | `0.0.0.0` | Bind address. **Keep on a private network only.** |
| `OWLMASK_PDF_PORT` | `9070` | Bind port. |
| `OWLMASK_PDF_AUTH_TOKEN` | (unset) | Shared bearer secret, at least 32 characters. **Required** — startup fails until set. |
| `OWLMASK_PDF_MAX_INPUT_BYTES` | `26214400` (25 MB) | Max upload size. |
| `OWLMASK_PDF_MAX_PAGES` | `200` | Max pages. |
| `OWLMASK_PDF_MIN_DPI` / `OWLMASK_PDF_MAX_DPI` | `72` / `300` | Allowed render DPI range. |
| `OWLMASK_PDF_MAX_RENDERED_PIXELS` | `250000000` | Total rendered-pixel budget (`dpi × pages`). |
| `OWLMASK_PDF_MAX_DECODED_STREAM_BYTES` | `536870912` (512 MB) | Decoded-stream budget. |
| `OWLMASK_PDF_MAX_REGIONS` | `10000` | Maximum redaction rectangles per request. |
| `OWLMASK_PDF_TIMEOUT` | `120` | Per-request wall-clock timeout (seconds). |
| `OWLMASK_PDF_MAX_CONCURRENCY` | `2` | Concurrent redactions; excess is rejected (fail closed). |

> ⚠️ **Security posture.** This service accepts arbitrary file uploads and renders
> potentially hostile PDFs. Bind it to a **private/internal network only**, never a
> public interface, behind the shared bearer token. The resource limits above exist
> because malicious PDFs can otherwise exhaust memory/CPU.

## Build & run

Requires the `owlmask-share-pdf` module in your local Maven repo first:

```bash
# 1. Install the shared engine (owlmask-share reactor)
mvn -f ../owlmask-share/pom.xml install

# 2a. Run from source
export OWLMASK_PDF_AUTH_TOKEN="$(openssl rand -hex 32)"
mvn spring-boot:run

# 2b. Or build and run the jar
mvn clean package
java -jar target/owlmask-pdf-1.0.0-SNAPSHOT.jar

# health check
curl -s http://127.0.0.1:9070/health
```

### Docker

The multi-stage `Dockerfile` builds the shared engine and this service together,
so the build context must be the **workspace root** that contains both
`owlmask-share/` and `owlmask-pdf/`:

```bash
# run from the workspace root (the directory containing owlmask-share/ and owlmask-pdf/)
docker build -f owlmask-pdf/Dockerfile -t owlmask-pdf .
docker run --rm -p 127.0.0.1:9070:9070 -e OWLMASK_PDF_AUTH_TOKEN="$OWLMASK_PDF_AUTH_TOKEN" owlmask-pdf
```

The image runs as a non-root user and exposes `9070`. In practice the service is
run on the internal Docker network alongside `owlmask-sdk` with **no published
port** — see `owlmask-sdk/docker-compose.yml`.

## Test

```bash
mvn test
```

Covers health, bearer authentication, the multipart contract, and the verified
`X-*` response headers through the real raster engine (`@SpringBootTest`). The
engine's own reconstruction / hash-binding / limit / rotated-crop tests live in
`owlmask-share-pdf`.

## Third-party

See [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md). Runtime dependencies
(Spring Boot and Apache PDFBox) are permissively licensed; neither imposes
copyleft obligations on this proprietary service.

---

Copyright (c) 2026 Yehoshua Zev Susswein. All Rights Reserved.
