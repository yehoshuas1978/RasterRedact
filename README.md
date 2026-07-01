# owlmask-pdf

Private Java service that reconstructs uploaded PDFs as image-only documents and
destroys hash-bound normalized redaction regions at the pixel level.

## Run locally

```bash
mvn -f ../owlmask-share/pom.xml install
OWLMASK_PDF_AUTH_TOKEN=change-me mvn spring-boot:run
```

The service exposes `GET /health` and authenticated multipart
`POST /pdf/redact`. It returns no document when validation, limits, rendering,
or verification fails.
