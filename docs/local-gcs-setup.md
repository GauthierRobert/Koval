# Local GCS setup (fake-gcs-server)

For local development we run a Google Cloud Storage emulator
([`fsouza/fake-gcs-server`](https://github.com/fsouza/fake-gcs-server)) instead
of provisioning a real GCP bucket. It speaks the same JSON API and supports
V4 signed URLs, so the backend code path is identical to production.

## Start the emulator

```bash
docker compose up -d fake-gcs
```

The service exposes `http://localhost:4443`. Data is persisted under
`./fake-gcs-data/` (gitignored except for the placeholder bucket folder so
the bucket name is stable across machines).

## Backend configuration

Set these environment variables before starting the backend:

```bash
export GCS_MEDIA_ENABLED=true
export GCS_MEDIA_BUCKET=koval-media-dev
export GCS_MEDIA_ENDPOINT=http://localhost:4443
export GCP_PROJECT_ID=local-dev
```

Then:

```bash
cd backend && mvn spring-boot:run
```

What happens:
- `MediaStorageConfig` sees `endpoint-url` is set → builds a `Storage` client
  that talks to the emulator with `NoCredentials`.
- `MediaBucketBootstrap` runs once on app startup, creates the bucket if
  missing (only in emulator mode — never in production).
- All `MediaStorageService` calls (signed URL upload, signed URL read,
  upload variants, delete) go through the emulator.

## Manual sanity check

After the backend is running:

```bash
# Request an upload URL (replace JWT)
curl -X POST http://localhost:8080/api/media/upload-url \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{"purpose":"GAZETTE_POST","contentType":"image/jpeg","sizeBytes":50000,"clubId":"abc"}'

# → returns { mediaId, signedUrl, ... }
# Upload directly to the emulator using the signed URL
curl -X PUT "$SIGNED_URL" \
  -H "Content-Type: image/jpeg" \
  --data-binary @photo.jpg

# Confirm
curl -X POST "http://localhost:8080/api/media/$MEDIA_ID/confirm" \
  -H "Authorization: Bearer $JWT"

# → triggers the variant pipeline + BlurHash, then returns the response
```

## Inspecting stored objects

You can browse the emulator's data directly on disk under
`./fake-gcs-data/<bucket>/...`. Each variant is at
`gazette-post/<scope>/<yyyy>/<mm>/<uuid>/{thumb,small,medium,large}.jpg`.

Or hit the JSON API directly:

```bash
curl http://localhost:4443/storage/v1/b/koval-media-dev/o
```

## Switching back to real GCS

Unset `GCS_MEDIA_ENDPOINT` (or leave it blank in `application.yml`). The
client will then use Application Default Credentials
(`GOOGLE_APPLICATION_CREDENTIALS` env var or Workload Identity in
GKE/Cloud Run) and the real `storage.googleapis.com` host.

## Limitations of the emulator

- HEIC handling: same as in prod (JDK ImageIO has no HEIC decoder, so the
  variant pipeline will fail gracefully and the original is served as-is).
- No IAM enforcement — anything goes. Don't rely on the emulator to validate
  permissions logic; cover those paths with unit tests against `MediaService`.
- Signed URL semantics are close but not bit-perfect to GCS. Sufficient for
  development and integration tests.
