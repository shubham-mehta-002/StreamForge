# StreamForge

A full-stack adaptive video streaming platform. Upload a video → auto-transcoded to 4 HLS quality tiers → streamed with adaptive bitrate playback via hls.js.

---

## Tech Stack

| Layer | Tech |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Data JPA |
| Database | MySQL 8 (AWS RDS) |
| Cache | Redis (Upstash, TLS) |
| Storage | AWS S3 (direct client upload via presigned URLs) |
| Trigger | AWS Lambda (S3 → webhook → encoding) |
| Video | FFmpeg + FFprobe |
| Frontend | Next.js 16 (App Router), TypeScript, Tailwind CSS |
| HLS Player | hls.js 1.6 (native Safari fallback) |
| UI | shadcn/ui + Radix UI |

---

## Architecture

```
Client (Next.js)
  │
  ├─ POST /videos/multipart/initiate  →  Server creates video record, returns uploadId + presigned URLs
  ├─ PUT  parts → S3 directly (parallel, 5 MB chunks, 6 concurrent)
  ├─ POST /videos/multipart/complete  →  Server calls S3 CompleteMultipartUpload
  │
  └─ S3 ObjectCreated event → AWS Lambda → POST /internal/s3/uploaded
                                               │
                                        Spring @Async thread
                                               │
                                    ┌──────────▼──────────┐
                                    │  FFmpeg encoding     │
                                    │  360p / 480p /       │
                                    │  720p / 1080p HLS    │
                                    └──────────┬──────────┘
                                               │
                                    Upload to S3 (encoded/)
                                               │
                                    Video → READY in MySQL
                                               │
                              GET /stream  →  Redis cache (TTL 1h)  →  hls.js
```

---

## Features

- **S3 Multipart Upload** — 5 MB chunks in parallel (max 6 concurrent), per-part ETag collection, abort on cancel/error
- **Adaptive Bitrate Encoding** — transcodes to all quality tiers ≤ source resolution (no upscaling)
- **HLS Streaming** — master playlist + per-quality playlists served directly from S3
- **Redis Cache-Aside** — HLS URL cached for 1 hour, eliminates DB hits on the hot streaming path
- **Auto Status Polling** — frontend polls every 5 seconds, stops automatically on READY / FAILED
- **File Validation** — MIME type check + 2 GB size limit enforced before any API call

---

## Application Flow

```
1. User selects file → validated (type + size) → POST /multipart/initiate
2. File split into 5 MB chunks → all parts PUT directly to S3 in parallel (max 6 concurrent)
3. Client sorts parts by partNumber ASC → POST /multipart/complete → server forwards to S3 → S3 assembles final object → ObjectCreated fires
4. Lambda → POST /internal/s3/uploaded (secret validated) → status = PROCESSING5. @Async thread: download → ffprobe → encode all profiles → master.m3u8
6. Upload encoded/ to S3 → status = READY, hlsMasterUrl saved
7. Client polls /status every 5s → on READY fetches /stream → hls.js plays from S3
```

---

## S3 Object Layout

```
videos/<videoId>/<fileName>              ← raw upload
encoded/<videoId>/master.m3u8            ← HLS master playlist
encoded/<videoId>/1080p/playlist.m3u8
encoded/<videoId>/1080p/segment_000.ts
... (same for 720p, 480p, 360p)
```

---

## Encoding Profiles

| Profile | Resolution | Bitrate | Audio |
|---|---|---|---|
| 1080p | 1920 × 1080 | 5,000 kbps | AAC 128k |
| 720p | 1,280 × 720 | 2,800 kbps | AAC 128k |
| 480p | 854 × 480 | 1,400 kbps | AAC 128k |
| 360p | 640 × 360 | 800 kbps | AAC 128k |

Codec: H.264 (`libx264`), CRF 23, preset `veryfast`, 3-second HLS segments.

---

## API

### Public — `/videos`

| Method | Path | Description |
|---|---|---|
| `POST` | `/videos/multipart/initiate` | Start multipart upload — returns `uploadId` + presigned URLs per part |
| `POST` | `/videos/multipart/complete` | Assemble S3 parts into final object |
| `POST` | `/videos/multipart/abort` | Cancel upload, clean up S3 parts |
| `GET` | `/videos` | All READY videos (id, filename, streamingUrl) |
| `GET` | `/videos/{id}/status` | Current pipeline status |
| `GET` | `/videos/{id}/stream` | HLS master URL (Redis-cached) |

### Internal — `/internal/s3`

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/internal/s3/uploaded` | `X-Webhook-Secret` | S3 event from Lambda → triggers encoding |

---

## Local Setup

**Prerequisites:** Java 21, Node 18, Docker, FFmpeg, AWS account + S3 bucket + Lambda trigger

### Backend
```bash
cd server/streamforge
docker compose up -d          # starts MySQL + Redis
./mvnw spring-boot:run        # API at http://localhost:8080
```

### Frontend
```bash
cd client
npm install
# set NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/videos in .env.local
npm run dev                   # app at http://localhost:3000
```

### Key environment variables (application.properties)

| Variable | Description |
|---|---|
| `aws.bucket-name` | S3 bucket name |
| `aws.region` | Must match actual bucket region (mismatch causes CORS failures) |
| `aws.access-key` / `aws.secret-key` | IAM credentials |
| `aws.lambda-webhook-secret` | Shared secret for Lambda webhook auth || `ffmpeg.path` / `ffprobe.path` | Absolute paths to FFmpeg binaries |
| `encoding.temp-dir` | Local temp directory for encoding jobs |
| `redis-ttl` | HLS URL cache TTL in seconds (default: 3600) |

---

## Roadmap

- [ ] Authentication (userId field already on Video entity)
- [ ] Sprite sheet generation for seek preview
- [ ] Real-time status via WebSocket/SSE (directory already exists)
- [ ] CDN (CloudFront) for HLS segment delivery

---

## Known Limitations (Demo Scope)

### No server-side file size enforcement
The 2 GB file size limit is enforced **client-side only** via `validateVideoFile()`. Neither the server nor S3 knows the expected file size when issuing presigned URLs — S3 will accept whatever bytes are PUT to the URL regardless of size.

**In production you would:**
- For multipart: require `fileSize` in `MultipartInitRequest`, validate it on the server before calling S3, and use [S3 upload policies](https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-HTTPPOSTConstructPolicy.html) to enforce a `content-length-range` condition baked into the presigned URL itself
- For single-part: pass `contentLength` to `PutObjectRequest.builder()` — S3 then rejects any PUT that doesn't match that exact byte count

### No authentication
CORS is open (`*`) and there is no auth layer. Any client can upload, poll, or stream any video. The `userId` field on the `Video` entity is commented out.

### Client-side only CORS for HLS
The S3 bucket must be configured with a CORS policy allowing GET requests from the frontend origin. There is no server-side proxy for HLS segment delivery — segments are fetched directly from S3 by the browser.
