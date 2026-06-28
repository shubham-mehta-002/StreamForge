# StreamForge

> A production-grade adaptive video streaming platform. Upload raw video files and serve them as multi-quality HLS streams — powered by AWS S3, FFmpeg, Spring Boot, and Next.js.

---

## Table of Contents

- [Overview](#overview)
- [Live Architecture](#live-architecture)
- [Tech Stack](#tech-stack)
- [Features](#features)
- [Application Flow](#application-flow)
  - [1. Upload Initialization](#1-upload-initialization)
  - [2. Direct S3 Upload](#2-direct-s3-upload)
  - [3. AWS Lambda Webhook Trigger](#3-aws-lambda-webhook-trigger)
  - [4. Async Video Encoding Pipeline](#4-async-video-encoding-pipeline)
  - [5. Streaming with Redis Cache](#5-streaming-with-redis-cache)
- [Project Structure](#project-structure)
- [API Reference](#api-reference)
- [Encoding Profiles](#encoding-profiles)
- [Database Schema](#database-schema)
- [Frontend Pages & Components](#frontend-pages--components)
- [Architecture Patterns](#architecture-patterns)
- [Local Development Setup](#local-development-setup)
  - [Prerequisites](#prerequisites)
  - [Backend](#backend)
  - [Frontend](#frontend)
  - [Docker (Infrastructure)](#docker-infrastructure)
- [Environment Variables](#environment-variables)
- [Roadmap / Planned Features](#roadmap--planned-features)

---

## Overview

StreamForge is an end-to-end video streaming platform that handles the full lifecycle of a video — from raw file upload through transcoding to adaptive bitrate delivery. Videos are uploaded directly to AWS S3 (bypassing the server), automatically transcoded to up to four quality levels (360p–1080p) using FFmpeg, packaged as HLS streams, and served to an HLS.js-powered browser player with adaptive quality switching.

The system is designed around event-driven, asynchronous processing: an AWS Lambda function bridges the gap between S3 upload completion and the backend encoding job, keeping the upload path non-blocking and the server stateless with respect to file I/O.

---

## Live Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          CLIENT  (Next.js 16)                           │
│  ┌──────────────┐   ┌───────────────────┐   ┌──────────────────────┐   │
│  │  VideoUpload │   │   UploadStatus    │   │    VideoGallery       │   │
│  │  (dropzone)  │   │  (poll + preview) │   │  (HLS player grid)   │   │
│  └──────┬───────┘   └────────┬──────────┘   └──────────────────────┘   │
│         │ POST /init-upload  │ GET /status                              │
│         │ PUT → S3 presigned │ GET /stream                              │
└─────────┼────────────────────┼──────────────────────────────────────────┘
          │                    │
          ▼                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     BACKEND  (Spring Boot 3 / Java 21)                  │
│                                                                         │
│  VideoUploadController  ──►  VideoService  ──►  VideoRepository         │
│  S3WebhookController    ──►  UploadProcessingService                    │
│  StreamingController    ──►  StreamingService  ──►  Redis Cache         │
│                                                  (cache-aside, TTL 60s) │
│                                                                         │
│  EncodingService  [@Async]                                              │
│    └── FFmpegService (probe + HLS encode)                               │
│    └── S3Service (download raw, upload encoded)                         │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
          ┌──────────────────┼──────────────────────┐
          │                  │                       │
          ▼                  ▼                       ▼
   ┌─────────────┐   ┌──────────────┐   ┌───────────────────────────────┐
   │  MySQL (RDS)│   │    Redis     │   │          AWS S3               │
   │  video meta │   │  HLS URL     │   │  videos/<id>/raw              │
   │  + status   │   │  cache       │   │  encoded/<id>/master.m3u8     │
   └─────────────┘   └──────────────┘   │  encoded/<id>/<quality>/*.ts  │
                                        └───────────────────────────────┘
                                                     ▲
                                          ┌──────────┴───────┐
                                          │   AWS Lambda     │
                                          │  (S3 trigger →   │
                                          │  POST /internal/ │
                                          │  s3/uploaded)    │
                                          └──────────────────┘
```

---

## Tech Stack

### Backend

| Component | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| ORM | Spring Data JPA / Hibernate |
| Database | MySQL 8 (AWS RDS) |
| Caching | Redis via Spring Data Redis (Upstash managed, TLS) |
| Object Storage | AWS S3 SDK v2 (`software.amazon.awssdk:s3`) |
| Video Processing | FFmpeg + FFprobe (local binaries, configurable paths) |
| Async Processing | Spring `@Async` with thread pool executor |
| Validation | Spring Boot Validation (Bean Validation 3) |
| Build | Maven (Maven Wrapper included) |
| Utilities | Lombok, Jackson |
| Message Queue | Apache Kafka (scaffolded — not yet active in production) |

### Frontend

| Component | Technology |
|---|---|
| Framework | Next.js 16.2.9 (App Router) |
| Language | TypeScript 5 |
| Styling | Tailwind CSS v4 |
| UI Primitives | shadcn/ui + Radix UI |
| HTTP Client | Axios 1.x |
| HLS Playback | hls.js 1.6 (+ native Safari HLS fallback) |
| File Upload | react-dropzone |
| Notifications | Sonner (toast) |
| Data Fetching | Custom React hooks (`useVideoUpload`, `useVideoStatus`, `useVideos`) |
| Fonts | Geist Sans / Geist Mono |

### Infrastructure

| Component | Technology |
|---|---|
| Cloud Storage | AWS S3 (`eu-north-1`) |
| Relational DB | AWS RDS MySQL (`ap-south-1`) |
| Cache | Upstash Redis (managed, TLS) |
| Serverless Trigger | AWS Lambda (S3 event → backend webhook) |
| Containerization | Docker Compose (MySQL + Redis for local dev) |

---

## Features

### Video Upload
- Drag-and-drop or click-to-select file picker
- Accepts MP4, MOV, AVI, MKV, WebM (any `video/*` MIME type)
- Supports files up to 2 GB
- Real-time upload progress bar with percentage and byte counts
- **Direct-to-S3 upload via presigned PUT URL** — the backend never proxies file bytes, keeping the server lightweight and maximizing throughput

### Adaptive Video Encoding
- Automatic transcoding to multiple quality tiers after upload
- **Only encodes resolutions at or below the source video** — no upscaling
- HLS packaging: 3-second segments with a complete segment list (no DVR window trimming)
- Master playlist (`master.m3u8`) with all available streams for client-side adaptive bitrate switching
- FFprobe-based metadata extraction: resolution, duration, file size, original filename

### Adaptive Bitrate Streaming (ABR)
- HLS master playlist served per video containing all available quality streams
- hls.js automatically selects the best quality based on available bandwidth
- Fallback to native HLS for Safari / iOS
- Graceful error overlay for network errors, media errors, and unsupported browsers
- `crossOrigin="anonymous"` to correctly handle CORS from S3

### Video Gallery
- Responsive grid (1 / 2 / 3 columns) of all ready-to-stream videos
- Inline HLS player per card — no navigation required
- Hover title overlay on each video
- Loading skeleton / error state handling

### Processing Status Tracking
- Upload page transitions to a status card immediately after upload
- Status badge reflects current pipeline stage: `REQUESTED` → `PROCESSING` → `READY` / `FAILED`
- Manual refresh button to poll the latest status
- Automatic Sonner toast notification on completion or failure
- Inline video preview loads automatically once status reaches `READY`

### Streaming URL Cache
- Redis cache-aside pattern on HLS master URL lookup
- Cache TTL: 60 seconds (configurable)
- Eliminates redundant DB hits for the hot streaming path

### Event-Driven Webhook Architecture
- S3 event notifications trigger an AWS Lambda function on `ObjectCreated`
- Lambda calls the internal `POST /internal/s3/uploaded` endpoint with a shared secret header
- 403 returned immediately on invalid secret — no processing occurs
- Decouples S3 upload completion from backend encoding trigger cleanly

### Async Non-Blocking Encoding
- `@Async` annotation on the encoding entry point runs each job on a Spring-managed thread pool
- The HTTP response to the Lambda webhook returns immediately (200 OK)
- Encoding runs in the background without blocking any web threads
- Cleanup of all temporary files guaranteed via `finally` block regardless of success or failure

---

## Application Flow

### 1. Upload Initialization

```
User selects file → VideoUpload component
       │
       ▼
POST /videos/init-upload  { fileName, contentType }
       │
       ▼
Server:
  • Generates UUID as videoId
  • Constructs S3 key: "videos/<videoId>/<fileName>"
  • Persists Video record with status = REQUESTED
  • Calls S3Presigner → presigned PUT URL (10-min expiry)
  • Returns: { videoId, uploadUrl, s3Key, expiresIn: 600 }
```

### 2. Direct S3 Upload

```
Client receives presigned URL
       │
       ▼
PUT <presignedUrl>  (body = raw file bytes)
  • Content-Type header set to file's MIME type
  • Axios onUploadProgress → real-time progress bar update
  • File lands in S3: videos/<videoId>/<fileName>
  • Server is NOT involved in this data transfer
```

### 3. AWS Lambda Webhook Trigger

```
S3 ObjectCreated event fires
       │
       ▼
AWS Lambda invoked with S3 event payload
       │
       ▼
Lambda → POST /internal/s3/uploaded
         Header: X-Webhook-Secret: <secret>
         Body:   { bucket: "streamforge-videos", key: "videos/<videoId>/..." }
       │
       ▼
S3WebhookController:
  • Validates X-Webhook-Secret — returns 403 if mismatch
  • Extracts videoId from key path
  • Updates status → PROCESSING
  • Calls EncodingService.encodeVideo(videoId, s3Key)  [@Async]
  • Returns 200 OK immediately
```

### 4. Async Video Encoding Pipeline

```
EncodingService.encodeVideo()  [runs on async thread pool]
       │
       ├─ Creates job temp directories:
       │     {encoding.temp-dir}/{videoId}/{jobUUID}/raw/
       │     {encoding.temp-dir}/{videoId}/{jobUUID}/encoded/
       │
       ├─ Downloads raw video from S3 → local temp path
       │
       ├─ Runs FFprobe → extracts: width, height, duration, fileSize
       │
       ├─ For each VideoProfile where profile.height ≤ source height:
       │     • Creates encodedDir/<profile>/
       │     • FFmpeg command:
       │         -vf scale=<W>:<H>          → resize to profile resolution
       │         -c:v libx264 -preset veryfast -crf 23
       │         -maxrate <maxKbps>k  -bufsize <bufKbps>k
       │         -c:a aac -b:a 128k
       │         -hls_time 3             → 3-second segments
       │         -hls_list_size 0        → keep all segments
       │         -hls_segment_filename segment_%03d.ts
       │         -f hls  playlist.m3u8
       │
       ├─ Generates master.m3u8:
       │     #EXT-X-STREAM-INF:BANDWIDTH=...,RESOLUTION=...,CODECS="avc1.42E01E,mp4a.40.2"
       │     <profile>/playlist.m3u8
       │
       ├─ Uploads entire encodedDir/ to S3:
       │     encoded/<videoId>/master.m3u8
       │     encoded/<videoId>/1080p/playlist.m3u8
       │     encoded/<videoId>/1080p/segment_000.ts  ...
       │     encoded/<videoId>/720p/playlist.m3u8
       │     ... (same for 480p, 360p)
       │
       ├─ Updates Video record:
       │     status = READY
       │     hlsMasterUrl = https://streamforge-videos.s3.amazonaws.com/encoded/<videoId>/master.m3u8
       │     width, height, duration, fileSize, processedAt
       │
       └─ On any failure:
             status = FAILED
             failureReason = exception message
             finally: deletes all temp files
```

### 5. Streaming with Redis Cache

```
User clicks Refresh on UploadStatus  (or Gallery loads)
       │
       ▼
GET /videos/<videoId>/status
  → Returns current { videoId, status }
  → When READY: client calls GET /videos/<videoId>/stream

GET /videos/<videoId>/stream
       │
       ▼
StreamingService:
  ├─ Check Redis key "video::hls::<videoId>"
  │     HIT  → return cached HLS URL immediately
  │     MISS → fetch from DB, write to Redis (TTL 60s), return URL
       │
       ▼
Client: hls.js loads master.m3u8 from S3
  • Parses available quality streams
  • Auto-selects stream based on bandwidth
  • Fetches .ts segments directly from S3 CDN
  • Switches quality levels seamlessly on bandwidth change
```

---

## Project Structure

```
StreamForge/
├── client/                          # Next.js frontend
│   ├── app/
│   │   ├── page.tsx                 # Home — Video Gallery
│   │   └── upload/page.tsx          # Upload page
│   ├── components/
│   │   ├── video/
│   │   │   ├── VideoUpload.tsx      # Dropzone + upload trigger
│   │   │   ├── UploadStatus.tsx     # Post-upload status + preview
│   │   │   ├── VideoPlayer.tsx      # hls.js player component
│   │   │   └── VideoGallery.tsx     # Responsive video grid
│   │   └── ui/                      # shadcn/ui primitives
│   ├── hooks/
│   │   ├── useVideoUpload.ts        # Upload state machine
│   │   ├── useVideoStatus.ts        # Status polling + stream URL fetch
│   │   └── useVideos.ts             # Gallery video list fetch
│   ├── services/
│   │   └── api.ts                   # All Axios API calls
│   ├── types/
│   │   └── video.ts                 # TypeScript interfaces
│   └── lib/
│       └── utils.ts                 # cn() utility
│
└── server/streamforge/              # Spring Boot backend
    └── src/main/java/com/stream_forge/streamforge/
        ├── config/
        │   └── CorsConfig.java      # CORS configuration
        ├── entity/
        │   ├── Video.java           # JPA entity
        │   └── VideoStatus.java     # REQUESTED / PROCESSING / READY / FAILED
        ├── exception/               # Domain exceptions + GlobalExceptionHandler
        ├── services/
        │   ├── video/               # VideoService + VideoUploadController (public API)
        │   ├── streaming/           # StreamingService + StreamingController
        │   ├── upload/              # UploadProcessingService + S3WebhookController
        │   └── encoding/
        │       ├── service/         # EncodingService + FFmpegService + S3Service
        │       ├── model/           # VideoProfile, VideoMetadata, VideoJobContext
        │       └── util/            # JobContextFactory
        └── StreamforgeApplication.java
```

---

## API Reference

### Public Endpoints — `/videos`

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/videos/init-upload` | Initialize upload — returns presigned S3 PUT URL |
| `GET` | `/videos` | List all READY videos |
| `GET` | `/videos/{videoId}/status` | Get current processing status |
| `GET` | `/videos/{videoId}/stream` | Get HLS master playlist URL (Redis-cached) |

**POST /videos/init-upload**

Request:
```json
{
  "fileName": "my-video.mp4",
  "contentType": "video/mp4"
}
```

Response `200 OK`:
```json
{
  "videoId": "a1b2c3d4-...",
  "uploadUrl": "https://streamforge-videos.s3.amazonaws.com/videos/a1b2c3d4.../my-video.mp4?X-Amz-...",
  "s3Key": "videos/a1b2c3d4-.../my-video.mp4",
  "expiresIn": 600
}
```

**GET /videos/{videoId}/status**

Response `200 OK`:
```json
{
  "videoId": "a1b2c3d4-...",
  "status": "PROCESSING"
}
```

**GET /videos/{videoId}/stream**

Response `200 OK`:
```json
{
  "videoId": "a1b2c3d4-...",
  "streamingUrl": "https://streamforge-videos.s3.amazonaws.com/encoded/a1b2c3d4-.../master.m3u8"
}
```

**GET /videos**

Response `200 OK`:
```json
[
  {
    "videoId": "a1b2c3d4-...",
    "originalFileName": "my-video.mp4",
    "streamingUrl": "https://streamforge-videos.s3.amazonaws.com/encoded/a1b2c3d4-.../master.m3u8"
  }
]
```

### Internal Endpoints — `/internal/s3`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/internal/s3/uploaded` | `X-Webhook-Secret` header | Receives S3 event from AWS Lambda |

Request:
```json
{
  "bucket": "streamforge-videos",
  "key": "videos/a1b2c3d4-.../my-video.mp4"
}
```

Response: `200 OK` (empty body) or `403 Forbidden` on secret mismatch.

---

## Encoding Profiles

StreamForge encodes uploaded videos into the following quality tiers. A profile is skipped if the source video has a lower height than the profile — no upscaling ever occurs.

| Profile | Resolution | Video Bitrate | Max Rate | Buffer Size | Audio |
|---|---|---|---|---|---|
| 1080p | 1920 × 1080 | 5,000 kbps | 5,350 kbps | 7,500 kbps | AAC 128k |
| 720p | 1,280 × 720 | 2,800 kbps | 2,996 kbps | 4,200 kbps | AAC 128k |
| 480p | 854 × 480 | 1,400 kbps | 1,498 kbps | 2,100 kbps | AAC 128k |
| 360p | 640 × 360 | 800 kbps | 856 kbps | 1,200 kbps | AAC 128k |

**Codec:** H.264 (`libx264`), preset `veryfast`, CRF 23  
**Segment duration:** 3 seconds  
**Container:** MPEG-TS (`.ts`)  
**Playlist format:** HLS v3

---

## Database Schema

Table: `videos`

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | `VARCHAR` PK | No | UUID assigned at upload init |
| `originalFileName` | `VARCHAR` | No | Original filename as uploaded |
| `s3OriginalKey` | `VARCHAR` | No | S3 object key of the raw video |
| `hlsMasterUrl` | `VARCHAR` | Yes | Public S3 URL to `master.m3u8`, set on `READY` |
| `thumbnailUrl` | `VARCHAR` | Yes | (Planned) Thumbnail image URL |
| `spriteUrl` | `VARCHAR` | Yes | (Planned) Sprite sheet URL for seek preview |
| `status` | `ENUM` | No | `REQUESTED` / `PROCESSING` / `READY` / `FAILED` |
| `duration` | `BIGINT` | Yes | Video duration in seconds |
| `fileSize` | `BIGINT` | Yes | File size in bytes |
| `width` | `INT` | Yes | Source video width in pixels |
| `height` | `INT` | Yes | Source video height in pixels |
| `failureReason` | `VARCHAR(1000)` | Yes | Exception message on `FAILED` status |
| `createdAt` | `DATETIME` | No | Auto-set on insert |
| `updatedAt` | `DATETIME` | No | Auto-updated on every write |
| `processedAt` | `DATETIME` | Yes | Set when encoding completes successfully |

---

## Frontend Pages & Components

### Pages

| Route | Component | Description |
|---|---|---|
| `/` | `app/page.tsx` | Home page — video gallery with upload button in header |
| `/upload` | `app/upload/page.tsx` | Upload page — file picker and status tracking |

### Components

**`VideoUpload`**  
Drag-and-drop zone (react-dropzone) that accepts `video/*` files. Displays file name and size on selection. Shows a real-time progress bar during upload. On successful upload, transitions to `UploadStatus`.

**`UploadStatus`**  
Displays current encoding pipeline status for the just-uploaded video. Shows a processing spinner while encoding, a success or failure badge when done, and a manual "Refresh" button. On `READY`, renders an inline `VideoPlayer` and a Sonner toast. On `FAILED`, shows the failure reason and a toast.

**`VideoPlayer`**  
Custom HLS player built on hls.js. Handles HLS loading, adaptive quality, fatal error recovery (network errors attempt resume, media errors attempt recovery), and native HLS fallback for Safari. Renders at 16:9 aspect ratio with a hover-activated title overlay.

**`VideoGallery`**  
Fetches all READY videos from `GET /videos` on mount. Renders a responsive 1/2/3-column grid of cards, each containing a `VideoPlayer` and the video's file name and ID. Shows a loading spinner while fetching and an empty-state message if no videos exist.

---

## Architecture Patterns

**Layered / Clean Architecture**  
Controllers handle HTTP concerns only. Services contain business logic behind interfaces. Infrastructure adapters (S3, Redis, FFmpeg) are isolated behind their own service interfaces with single implementations.

**Repository Pattern**  
`VideoRepository extends JpaRepository<Video, String>` — all database access through a single typed repository.

**Presigned URL (Bypass Pattern)**  
The backend generates a time-limited S3 presigned PUT URL and returns it to the client. The client uploads the file directly to S3 without the file bytes ever passing through the Spring server. This eliminates memory pressure, removes upload size limits from the application layer, and reduces latency.

**Event-Driven Trigger via Lambda Webhook**  
Rather than polling S3 or making the client notify the server of upload completion, an AWS Lambda function subscribes to S3 `ObjectCreated` events and calls a secured internal webhook endpoint. This decouples the upload from processing and ensures the encoding job starts only after S3 has durably received the file.

**Async Processing with @Async**  
The encoding pipeline runs on a Spring-managed thread pool. The HTTP response to the Lambda webhook returns immediately, keeping the webhook call short-lived and idempotent from Lambda's perspective.

**Cache-Aside Pattern**  
When a streaming URL is requested, the service checks Redis first. On a miss, it queries MySQL, caches the result with a 60-second TTL, and returns it. Subsequent requests within the TTL window never hit the database.

**Global Exception Handling**  
`@RestControllerAdvice` maps all domain exceptions (`EncodingException`, `VideoNotReadyToStreamException`, `VideoProbeException`, etc.) to appropriate HTTP status codes and structured error responses.

**Builder Pattern (Lombok)**  
All DTOs and entity update requests use `@Builder` for immutable, readable construction. `VideoUpdateRequest` is a dedicated record for partial updates — keeping entity mutation explicit and traceable.

**Interface Segregation**  
Every service has a dedicated interface (`VideoService`, `EncodingService`, `FFmpegService`, `S3Service`, `StreamingService`, `UploadProcessingService`). This enforces contract-based design and makes each component independently testable and swappable.

---

## Local Development Setup

### Prerequisites

- Java 21+
- Node.js 18+
- Docker & Docker Compose
- FFmpeg and FFprobe installed locally ([ffmpeg.org](https://ffmpeg.org/download.html))
- AWS account with an S3 bucket configured
- AWS Lambda configured to listen to the bucket's `ObjectCreated` events and POST to your server

---

### Backend

1. Copy and configure environment:

```bash
cd server/streamforge
cp .env.example .env   # fill in AWS keys, DB URL, Redis URL, FFmpeg paths
```

2. Start infrastructure (MySQL + Redis) via Docker:

```bash
docker compose up -d
```

3. Run the Spring Boot application:

```bash
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

---

### Frontend

1. Install dependencies:

```bash
cd client
npm install
```

2. Configure environment:

```bash
# client/.env.local
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/videos
```

3. Start the development server:

```bash
npm run dev
```

The app will be available at `http://localhost:3000`.

---

### Docker (Infrastructure)

The `docker-compose.yml` file in `server/streamforge/` starts MySQL and Redis locally. Kafka and Zookeeper are included as commented stubs for when the event streaming layer is activated.

```bash
# Start MySQL + Redis
docker compose up -d

# Stop and remove containers
docker compose down
```

| Service | Port | Credentials |
|---|---|---|
| MySQL | `3306` | `streamforge` / `streamforge`, DB: `streamforge_db` |
| Redis | `6379` | No auth (local dev) |

---

## Environment Variables

### Backend (`server/streamforge/.env` / `application.properties`)

| Variable | Description |
|---|---|
| `spring.datasource.url` | JDBC URL for MySQL (AWS RDS or local Docker) |
| `spring.datasource.username` | MySQL username |
| `spring.datasource.password` | MySQL password |
| `spring.data.redis.host` | Redis host (Upstash or local) |
| `spring.data.redis.port` | Redis port |
| `spring.data.redis.ssl.enabled` | `true` for Upstash TLS |
| `redis-ttl` | Streaming URL cache TTL in seconds (default: `60`) |
| `aws.bucket-name` | S3 bucket name |
| `aws.region` | AWS region (e.g. `eu-north-1`) |
| `aws.access-key` | AWS IAM access key |
| `aws.secret-key` | AWS IAM secret key |
| `aws.presigned-hls-playlist-url-expiry` | Presigned URL expiry in minutes |
| `aws.lambda-webhook-secret` | Shared secret validated on webhook calls |
| `ffmpeg.path` | Absolute path to the `ffmpeg` binary |
| `ffprobe.path` | Absolute path to the `ffprobe` binary |
| `encoding.temp-dir` | Local directory for temporary encoding files |

### Frontend (`client/.env.local`)

| Variable | Description |
|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | Backend API base URL (e.g. `http://localhost:8080/videos`) |

---

## Roadmap / Planned Features

- [ ] **Apache Kafka integration** — full event-driven encoding pipeline; Kafka topics (`video.uploaded`, `video.encoding.started`, `video.encoding.completed`, `video.encoding.failed`) are already defined
- [ ] **Authentication** — `userId` column is scaffolded in the `Video` entity; ready to wire in Spring Security
- [ ] **Thumbnail generation** — service stub exists; generate a thumbnail frame at a configurable timestamp using FFmpeg
- [ ] **Sprite sheet generation** — service stub exists; storyboard thumbnails for seek preview in the player
- [ ] **Real-time status updates** — WebSocket / SSE directory exists in the project; replace manual polling with push notifications
- [ ] **Video detail page** — dedicated route per video with full metadata display
- [ ] **Search and filtering** — filter gallery by name, duration, upload date
- [ ] **Docker multi-stage build** — Dockerfile stub exists for containerising the Spring Boot app
- [ ] **CORS hardening** — current config uses wildcard origins; restrict to known frontend origin in production
- [ ] **CDN integration** — serve HLS segments via CloudFront for lower latency at global scale
