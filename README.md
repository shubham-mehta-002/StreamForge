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
  ├─ PUT  parts → S3 directly (parallel, 10 MB chunks, 4 concurrent)
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
                                    │  + thumbnail JPEG    │
                                    └──────────┬──────────┘
                                               │
                                    Upload to S3 (encoded/ + thumbnails/)
                                               │
                                    Video → READY in MySQL
                                               │
                              GET /stream  →  Redis cache (TTL 1h)  →  hls.js
```

---

## Features

- **S3 Multipart Upload** — 10 MB chunks in parallel (max 4 concurrent), per-part ETag collection, abort on cancel/error
- **Adaptive Bitrate Encoding** — transcodes to all quality tiers ≤ source resolution (no upscaling)
- **Thumbnail Generation** — FFmpeg extracts a JPEG at 10% of video duration, uploaded to S3, shown as poster in the player
- **HLS Streaming** — master playlist + per-quality playlists served directly from S3
- **Redis Cache-Aside** — HLS URL cached for 1 hour, eliminates DB hits on the hot streaming path
- **Auto Status Polling** — frontend polls every 5 seconds, stops automatically on READY / FAILED
- **File Validation** — MIME type check + 2 GB size limit enforced before any API call
- **Non-fatal thumbnail** — if thumbnail generation fails, video still reaches READY

---

## Application Flow

```
1. User selects file → validated (type + size) → POST /multipart/initiate
2. File split into 10 MB chunks → all parts PUT directly to S3 in parallel
3. POST /multipart/complete → S3 assembles final object → ObjectCreated fires
4. Lambda → POST /internal/s3/uploaded (secret validated) → status = PROCESSING
5. @Async thread: download → ffprobe → encode all profiles → master.m3u8 → thumbnail
6. Upload encoded/ + thumbnails/ to S3 → status = READY, hlsMasterUrl + thumbnailUrl saved
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
thumbnails/<videoId>/thumbnail.jpg       ← poster image
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
| `GET` | `/videos` | All READY videos (id, filename, streamingUrl, thumbnailUrl) |
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
| `aws.lambda-webhook-secret` | Shared secret for Lambda webhook auth |
| `ffmpeg.path` / `ffprobe.path` | Absolute paths to FFmpeg binaries |
| `encoding.temp-dir` | Local temp directory for encoding jobs |
| `redis-ttl` | HLS URL cache TTL in seconds (default: 3600) |

---

## Roadmap

- [ ] Kafka event streaming (topics + events already scaffolded)
- [ ] Authentication (userId field already on Video entity)
- [ ] Sprite sheet generation for seek preview
- [ ] Real-time status via WebSocket/SSE (directory already exists)
- [ ] CDN (CloudFront) for HLS segment delivery
