# StreamForge — Backend

Spring Boot video processing server. Handles video uploads, encodes them to HLS format using FFmpeg, and serves streaming URLs to clients.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.5, Java 21 |
| Database | MySQL 8 (video metadata) |
| Cache | Redis (HLS URL cache) |
| Storage | AWS S3 |
| Encoding | FFmpeg |
| Infrastructure | Docker Compose (local), AWS Lambda (webhook trigger) |

---

## Project Structure

```
src/main/java/com/stream_forge/streamforge/
│
├── config/                         # CORS configuration
├── entity/                         # JPA entities (Video, VideoStatus)
├── exception/                      # Custom exceptions + global handler
│
├── infrastructure/
│   ├── redis/config/               # RedisTemplate configuration
│   └── s3/
│       ├── config/                 # S3Client + S3Presigner beans
│       └── service/                # S3PresignService — presigned URL generation, multipart lifecycle
│
└── services/
    ├── encoding/                   # FFmpeg encoding pipeline
    │   ├── config/                 # EncodingConfig (JobContextFactory bean)
    │   ├── model/                  # VideoJobContext, VideoMetadata, VideoProfile
    │   ├── service/                # EncodingService, FFmpegService, S3Service
    │   └── util/                   # JobContextFactory
    │
    ├── streaming/                  # HLS URL retrieval with Redis cache
    ├── upload/                     # S3 webhook controller (Lambda callback)
    └── video/                      # Upload lifecycle, video CRUD
        ├── controller/             # VideoUploadController
        ├── dto/
        │   ├── request/            # InitUploadRequest, MultipartInitRequest, etc.
        │   └── response/           # InitUploadResponse, MultipartInitResponse, etc.
        ├── repository/             # VideoRepository (JPA)
        └── service/                # VideoService + VideoServiceImpl
```

---

## API Endpoints

### Upload

| Method | Path | Description |
|---|---|---|
| `POST` | `/videos/multipart/initiate` | Start a multipart upload session |
| `POST` | `/videos/multipart/complete` | Assemble uploaded parts into final S3 object |
| `POST` | `/videos/multipart/abort` | Cancel upload, clean up S3 parts |
| `POST` | `/videos/init-upload` | Legacy single-part presigned URL (small files) |

### Video

| Method | Path | Description |
|---|---|---|
| `GET` | `/videos` | List all READY videos (gallery) |
| `GET` | `/videos/{videoId}/status` | Get processing status of a video |
| `GET` | `/videos/{videoId}/stream` | Get HLS master playlist URL |

### Internal

| Method | Path | Description |
|---|---|---|
| `POST` | `/internal/s3/uploaded` | Webhook called by Lambda after S3 upload completes |

---

## System Design

### Full Upload → Stream Flow

```
Client
  │
  ├─ POST /videos/multipart/initiate
  │       Server creates DB record (REQUESTED)
  │       Server calls S3 CreateMultipartUpload → gets uploadId
  │       Server generates presigned PUT URL per part
  │       Returns { videoId, uploadId, presignedUrls[] }
  │
  ├─ PUT each chunk directly to S3 using presigned URLs
  │       Client splits file into 10MB chunks
  │       Each chunk goes directly to S3 — server not involved
  │       S3 returns ETag per chunk (fingerprint for integrity check)
  │
  ├─ POST /videos/multipart/complete  { videoId, uploadId, parts[{partNumber, etag}] }
  │       Server calls S3 CompleteMultipartUpload with all ETags
  │       S3 verifies ETags, assembles all chunks into one object
  │       S3 fires ObjectCreated event
  │
  │   [AWS Lambda picks up the S3 event]
  │       Lambda calls POST /internal/s3/uploaded
  │         with X-Webhook-Secret header for authentication
  │
  ├─ Encoding pipeline starts (@Async — background thread)
  │       Status → PROCESSING
  │       Download raw video from S3 to local temp dir
  │       ffprobe → extract width, height, duration, fileSize
  │       FFmpeg → encode to HLS for each applicable profile:
  │           1080p / 720p / 480p / 360p (skips profiles above source resolution)
  │           Each profile produces: playlist.m3u8 + segment_000.ts, segment_001.ts ...
  │       Generate master.m3u8 (lists all quality variants)
  │       Upload encoded/ folder to S3 at encoded/{videoId}/
  │       Status → READY (or FAILED on any exception)
  │       Cleanup temp directory
  │
  └─ GET /videos/{videoId}/stream
          Check Redis cache (key: video::hls::{videoId})
          Cache hit  → return cached HLS URL
          Cache miss → fetch from DB, write to Redis (TTL: 1 hour), return URL
```

---

### Multipart Upload — Why ETags Matter

S3 multipart upload is a 3-step protocol:

**Step 1 — Initiate**
Server calls `CreateMultipartUpload` → S3 returns an `uploadId`. This is a session token. Every subsequent call must reference it.

**Step 2 — Upload parts**
Client splits the file and PUTs each chunk to a presigned URL. The presigned URL is locked to a specific `partNumber` and `uploadId` — you can't use part 1's URL for part 7. After each PUT, S3 returns an `ETag` in the response header. The ETag is S3's checksum of that chunk.

**Step 3 — Complete**
Client sends all `{ partNumber, etag }` pairs to the server. Server calls `CompleteMultipartUpload`. S3 verifies every ETag matches what was stored, then assembles all parts into the final object. If any ETag doesn't match, S3 rejects the complete call — this guarantees data integrity.

**Abort**
If the upload is cancelled or fails, `AbortMultipartUpload` must be called. S3 stores uploaded parts even if never completed and charges for them. Aborting deletes the parts and ends the session.

---

### Webhook Authentication (X-Webhook-Secret)

When a video upload completes, S3 fires an `ObjectCreated` event. A Lambda function picks this up and calls `POST /internal/s3/uploaded` on this server.

To prevent unauthorized calls to this internal endpoint, every request from Lambda must include:

```
X-Webhook-Secret: <shared-secret>
```

The server compares this against `aws.lambda-webhook-secret` in `application.properties`. Requests that don't match get a `403` response immediately.

---

### HLS — How Adaptive Bitrate Streaming Works

After encoding, S3 contains:

```
encoded/{videoId}/
  ├── master.m3u8          ← top-level playlist listing all quality options
  ├── 1080p/
  │     ├── playlist.m3u8  ← segment list for this quality
  │     ├── segment_000.ts ← 3-second video chunk
  │     ├── segment_001.ts
  │     └── ...
  ├── 720p/
  │     └── ...
  ├── 480p/
  │     └── ...
  └── 360p/
        └── ...
```

The client loads `master.m3u8` first. The HLS player reads it, sees the available quality options and their bandwidths, and picks the right one based on current network speed. As network conditions change, the player switches qualities automatically — this is adaptive bitrate streaming.

Source videos are never upscaled. A 720p source only gets 720p, 480p, and 360p profiles encoded.

---

### Redis Cache

HLS master URLs are cached in Redis using a cache-aside pattern:

```
GET /videos/{videoId}/stream
  → check Redis key: video::hls::{videoId}
  → HIT:  return cached URL
  → MISS: query DB → write to Redis (TTL: 1 hour) → return URL
```

The HLS master URL never changes once a video reaches READY status, so a 1-hour TTL is safe and eliminates repeated DB hits on the hot streaming path.

---

### Video Status Lifecycle

```
REQUESTED   → video record created, upload in progress
PROCESSING  → Lambda webhook received, encoding started
READY       → encoding complete, HLS URL available
FAILED      → encoding failed, failureReason stored in DB
```

---

### S3 Folder Structure

```
videos/
  └── {videoId}/{originalFileName}     ← raw uploaded file

encoded/
  └── {videoId}/
        ├── master.m3u8
        ├── 1080p/playlist.m3u8
        ├── 1080p/segment_NNN.ts
        └── ...
```

---

## Local Setup

### Prerequisites

- Java 21
- Maven
- Docker (for MySQL + Redis)
- FFmpeg — [download](https://ffmpeg.org/download.html)
- AWS account with an S3 bucket and an IAM user with S3 permissions

### 1. Start MySQL and Redis

```bash
docker-compose up -d
```

### 2. Configure application.properties

Copy the example file and fill in your values:

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Key values to set:

```properties
# Database
spring.datasource.username=YOUR_DB_USERNAME
spring.datasource.password=YOUR_DB_PASSWORD

# AWS
aws.bucket-name=YOUR_BUCKET
aws.region=YOUR_REGION
aws.access-key=YOUR_ACCESS_KEY
aws.secret-key=YOUR_SECRET_KEY
aws.lambda-webhook-secret=YOUR_SECRET

# FFmpeg (absolute paths)
ffmpeg.path=/usr/bin/ffmpeg
ffprobe.path=/usr/bin/ffprobe
encoding.temp-dir=/tmp/streamforge
```

### 3. Run

```bash
./mvnw spring-boot:run
```

Server starts on `http://localhost:8080`.

---

### Testing the Encoding Pipeline Locally

Since there is no Lambda in local development, you can trigger encoding manually by calling the webhook endpoint directly:

```bash
curl -X POST http://localhost:8080/internal/s3/uploaded \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Secret: YOUR_WEBHOOK_SECRET" \
  -d '{ "bucket": "your-bucket-name", "key": "videos/{videoId}/{fileName}" }'
```

The `key` must match an existing video record's `s3OriginalKey` in the database.

---

## Known Limitations

- No authentication — all endpoints are publicly accessible
- Encoding runs on Spring's default async thread pool with no concurrency limit
- All presigned URLs generated upfront — impractical for very large files (100s of GB)
- No upload resume support — failed uploads must restart from scratch
- `master.m3u8` includes all 4 quality entries regardless of which profiles were actually encoded (bug for low-resolution source videos)
