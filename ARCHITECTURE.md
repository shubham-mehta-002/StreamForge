# StreamForge — Architecture & Data Flow

---

## Key Concepts Used in This Project

### `@Async` — Non-blocking Encoding

`@Async` is a Spring annotation that runs a method on a **separate thread pool** instead of the thread that called it. The caller gets control back immediately — the method executes in the background concurrently.

**Why it matters here:**
When a video upload completes, AWS Lambda calls `POST /internal/s3/uploaded`. The server needs to kick off FFmpeg encoding, which can take **minutes** for a large video. Without `@Async`, the Lambda HTTP request would hang open for the entire duration, eventually timing out and possibly triggering duplicate retries.

With `@Async` on `EncodingService.encodeVideo()`:
```
Lambda → POST /internal/s3/uploaded
Server  → validates request
        → sets status = PROCESSING
        → calls encodeVideo()  ← @Async: returns immediately, encoding starts on background thread
Lambda ← 200 OK  (in milliseconds, not minutes)
        ... encoding continues in background ...
        ... DB updated to READY when done ...
```

The client never waits for encoding — it polls `GET /videos/{id}/status` every 5 seconds until status flips to `READY`.

> **Spring requirement:** `@Async` only works if `@EnableAsync` is present on a `@Configuration` class (or the main application class). StreamForge enables it on `StreamforgeApplication`.

---

### ETags — S3 Multipart Upload Integrity

An **ETag (Entity Tag)** is a hash/identifier that S3 generates for each uploaded chunk. It serves as a **data integrity token** — proof that a specific chunk arrived at S3 uncorrupted.

**Why it matters here:**
S3 multipart uploads work in three steps: initiate → upload parts → complete. When completing, S3 needs to know which parts to assemble and in what order. You can't just say "assemble all parts" — you must send back the ETag for each part so S3 can:

1. **Verify** it has the exact chunk you uploaded (not a corrupted or partial one)
2. **Identify** which part number maps to which physical data block
3. **Assemble** them in the correct order into the final object

```
Client uploads part 3 → S3 returns ETag: "abc123..."
Client uploads part 1 → S3 returns ETag: "def456..."
Client uploads part 2 → S3 returns ETag: "ghi789..."

POST /multipart/complete:
  parts: [
    { partNumber: 1, etag: "def456..." },
    { partNumber: 2, etag: "ghi789..." },
    { partNumber: 3, etag: "abc123..." }
  ]
→ S3 assembles in order 1 → 2 → 3 using these ETags to locate each chunk
```

In the code, `uploadPartToS3()` extracts the ETag from the `response.headers['etag']` of each S3 PUT response. These are collected into a `PartDetail[]` array, sorted ascending by `partNumber` (S3 requires this), then sent to `POST /videos/multipart/complete`.

> **Without ETags:** S3 would have no way to verify data integrity or correctly reassemble the chunks — the `CompleteMultipartUpload` call would be rejected.

---

### `uploadId` — S3 Multipart Session Token

`uploadId` is a token that **S3 generates and owns** — your code never creates it.

When the server calls `CreateMultipartUpload` on S3, S3 opens a session and responds with a unique string:

```
"VXBsb2FkIElEIGZvciA2aWWpbmcncyBteS1tb3ZpZS5tcDQ"
```

That string is the `uploadId`. It's S3's way of saying "I have opened a multipart session, and this is its ID." From that point on, every operation related to that upload must include this `uploadId`:

```
CreateMultipartUpload  → S3 returns uploadId
                               ↓
UploadPart(uploadId, partNumber=1, data)   ← S3 knows which session this chunk belongs to
UploadPart(uploadId, partNumber=2, data)
UploadPart(uploadId, partNumber=3, data)
                               ↓
CompleteMultipartUpload(uploadId, parts[])  ← S3 assembles THIS session's parts
         OR
AbortMultipartUpload(uploadId)              ← S3 cleans up THIS session's parts
```

**Why S3 needs it:**
S3 is stateless and handles millions of concurrent multipart uploads. The `uploadId` is how S3 knows which chunks belong to which upload. Without it, if two users uploaded the same filename at the same time, S3 would have no way to tell their parts apart.

**How it flows through this codebase:**
1. `VideoServiceImpl.initiateMultipartUpload()` calls S3 `CreateMultipartUpload` → S3 returns `uploadId`
2. Server sends it back to the client in the response
3. Client stores it in `sessionRef.current = { videoId, uploadId }`
4. Every presigned `UploadPart` URL already has the `uploadId` baked into its signature internally
5. On complete or abort, the client sends `uploadId` back to the server → server passes it straight to S3

It is a **session token** scoped to the duration of one upload. Once `CompleteMultipartUpload` or `AbortMultipartUpload` is called, the `uploadId` is consumed and can never be used again.

---

### `JobContextFactory` — Temp Path Management

FFmpeg is a command-line tool — it reads files from disk and writes output to disk. It can't work with S3 directly. So before encoding starts, the raw video must be downloaded to a local temp folder, and FFmpeg needs somewhere to write the encoded output. `JobContextFactory` builds and owns those paths.

**The directory structure it creates:**

```
{encoding.temp-dir}/
└── {videoId}/
    └── {jobUUID}/              ← jobDir
        ├── raw_video           ← inputFile  (S3 download lands here)
        └── encoded/            ← encodedDir (FFmpeg writes here)
            ├── 1080p/
            │   ├── playlist.m3u8
            │   └── segment_000.ts ...
            ├── 720p/  ...
            ├── 480p/  ...
            ├── 360p/  ...
            └── master.m3u8
```

**Why three levels deep — `tempDir / videoId / jobUUID`?**

Each level solves a different problem:
- **`tempDir`** — the root configured in `application.properties`. All encoding temp files go here.
- **`videoId`** — groups everything for one video together. Easy to find when debugging.
- **`jobUUID`** — the critical one. If the same video fails and gets reprocessed, or two encoding requests fire simultaneously, each run gets its own UUID folder. Without this, a second run would overwrite `raw_video` while the first run is still reading it — corrupted encoding.

**What each path is used for:**

- **`jobDir`** — root of this encoding run. The `finally` block in `EncodingServiceImpl` calls `cleanup(jobDir)` which deletes the entire tree — one call wipes everything, success or failure.
- **`inputFile`** — `S3ServiceImpl.download()` writes the raw video bytes here. Named `raw_video` with no extension — FFmpeg doesn't care about extensions.
- **`encodedDir`** — FFmpeg writes per-profile output here (`encoded/720p/`, `encoded/360p/` etc.). After all profiles finish, `S3ServiceImpl.uploadDirectory()` walks this entire folder and uploads everything to S3.

**`VideoJobContext`** is just a record that bundles all three paths together so `EncodingServiceImpl` doesn't have to manage them as separate variables:

```java
public record VideoJobContext(
    String movieId,
    Path jobDir,      // cleanup target
    Path inputFile,   // where to download raw video
    Path encodedDir   // where FFmpeg writes output
)
```

**Full lifecycle inside `EncodingServiceImpl`:**

```
ctx = contextFactory.create(videoId)
        ↓
Files.createDirectories(ctx.jobDir())       // create folders on disk
Files.createDirectories(ctx.encodedDir())
        ↓
s3.download(s3Key, ctx.inputFile())         // raw video saved to inputFile
        ↓
ffmpeg.encodeHls(ctx.inputFile(), outDir)   // reads inputFile, writes to encodedDir
        ↓
s3.uploadDirectory(ctx.encodedDir(), ...)   // uploads all of encodedDir to S3
        ↓
finally: cleanup(ctx.jobDir())              // deletes jobDir and everything inside
```

The factory creates it, the service uses it, the finally block destroys it.

**Why a separate factory class and not just inside `EncodingServiceImpl`?**
Path-building is a separate concern from encoding logic. The factory owns "how temp paths are structured" — `EncodingServiceImpl` stays focused on "how to encode." The `encoding.temp-dir` property also needs special wiring: since `JobContextFactory` takes a plain `String` constructor argument, Spring can't autowire it automatically. `EncodingConfig` handles this manually:

```java
// EncodingConfig.java
@Bean
public JobContextFactory jobContextFactory(@Value("${encoding.temp-dir}") String tempDir) {
    return new JobContextFactory(tempDir);
}
```

> **Note:** `EncodingConfig` is what reads `encoding.temp-dir` and passes it into `JobContextFactory` via constructor. `JobContextFactory` itself has no `@Value` annotation — it just receives `tempDir` as a plain constructor argument.

---

## What It Is

A full-stack adaptive video streaming platform. Users upload videos through a Next.js UI → files go directly to S3 → a Lambda webhook triggers encoding → Spring Boot transcodes via FFmpeg into HLS → the client polls for readiness and plays back with hls.js.

---

## Infrastructure Stack

```
┌─────────────────────────────────────────────────────────┐
│  Client (Next.js)          Server (Spring Boot)          │
│  localhost:3000            localhost:8080                 │
├─────────────────────────────────────────────────────────┤
│  MySQL 8.0 (Docker)        Redis 7 (Docker / Upstash)    │
│  streamforge_db            HLS URL cache (TTL 3600s)     │
├─────────────────────────────────────────────────────────┤
│  AWS S3                    AWS Lambda                    │
│  raw uploads + HLS output  S3 ObjectCreated → webhook    │
└─────────────────────────────────────────────────────────┘
```

FFmpeg runs **on the Spring Boot server** (not in a container). The server must have the FFmpeg binary installed and a local temp directory with enough disk space.

---

## Project Structure

```
StreamForge/
├── client/                         # Next.js frontend (App Router)
│   ├── app/
│   │   ├── layout.tsx              # Global layout — Toaster (sonner)
│   │   ├── page.tsx                # Home — VideoGallery
│   │   └── upload/
│   │       └── page.tsx            # Upload page — VideoUpload
│   ├── components/
│   │   ├── ui/                     # shadcn/ui primitives
│   │   └── video/
│   │       ├── VideoUpload.tsx     # Dropzone + upload phases
│   │       ├── UploadStatus.tsx    # Post-upload status & preview
│   │       ├── VideoGallery.tsx    # Grid of READY videos
│   │       └── VideoPlayer.tsx     # hls.js player with ABR badge
│   ├── hooks/
│   │   ├── useVideoUpload.ts       # Multipart upload orchestration
│   │   ├── useVideoStatus.ts       # 5s polling for video status
│   │   └── useVideos.ts            # Fetch all READY videos
│   ├── services/
│   │   └── api.ts                  # All backend + S3 API calls
│   ├── lib/
│   │   ├── axios.ts                # Shared axios instance
│   │   └── validateVideoFile.ts    # Size + MIME validation
│   └── types/
│       ├── video.ts                # Video / status types
│       └── multipart.ts            # Multipart upload types
│
└── server/streamforge/             # Spring Boot backend (Java 21)
    └── src/main/java/com/stream_forge/streamforge/
        ├── entity/
        │   ├── Video.java          # JPA entity — videos table
        │   └── VideoStatus.java    # REQUESTED/PROCESSING/READY/FAILED
        ├── services/
        │   ├── video/
        │   │   ├── controller/     # VideoUploadController (all endpoints)
        │   │   ├── service/        # VideoServiceImpl
        │   │   ├── dto/            # Request/Response DTOs
        │   │   └── repository/     # VideoRepository (JpaRepository)
        │   ├── encoding/
        │   │   ├── service/        # EncodingServiceImpl, FFmpegServiceImpl
        │   │   └── model/          # VideoProfile, VideoJobContext, VideoMetadata
        │   ├── streaming/
        │   │   └── service/        # StreamingServiceImpl (Redis cache-aside)
        │   └── upload/
        │       └── service/        # UploadProcessingServiceImpl (webhook handler)
        ├── config/                 # S3Config, RedisConfig, CorsConfig, EncodingConfig
        ├── infrastructure/
        │   ├── s3/                 # S3PresignServiceImpl, S3ServiceImpl
        │   ├── kafka/              # (empty — not yet implemented)
        │   └── websocket/          # (empty — not yet implemented)
        └── exception/              # GlobalExceptionHandler + custom exceptions
```

---

## Database Schema (table: `videos`)

| Column | Type | Notes |
|---|---|---|
| `id` | String (UUID) | Set by app, not DB auto-generated |
| `originalFileName` | String | NOT NULL |
| `s3OriginalKey` | String | e.g. `videos/<uuid>/<filename>` |
| `hlsMasterUrl` | String | Set after encoding completes |
| `spriteUrl` | String | Reserved — not yet populated |
| `status` | Enum (STRING) | `REQUESTED → PROCESSING → READY / FAILED` |
| `duration` | Long | Seconds, extracted by ffprobe |
| `fileSize` | Long | Bytes |
| `width` / `height` | Integer | From ffprobe |
| `failureReason` | String(1000) | Populated on FAILED status |
| `createdAt` | LocalDateTime | Auto — Hibernate `@CreationTimestamp` |
| `updatedAt` | LocalDateTime | Auto — Hibernate `@UpdateTimestamp` |
| `processedAt` | LocalDateTime | Set when encoding finishes |

Hibernate `ddl-auto=update` auto-creates/migrates the table on startup.

---

## VideoStatus Lifecycle

```
REQUESTED   → Presigned URL issued, waiting for S3 object to land
PROCESSING  → Lambda webhook received, FFmpeg encoding in progress
READY       → HLS files uploaded, hlsMasterUrl set, streamable
FAILED      → Encoding threw an exception (reason stored in failureReason)
```

---

## API Endpoints (Spring Boot — base: `/videos`)

### Upload Lifecycle

| Method | Path | Description |
|---|---|---|
| `POST` | `/videos/multipart/initiate` | Opens S3 multipart session, creates DB record, returns presigned URLs per chunk |
| `POST` | `/videos/multipart/complete` | Tells S3 to assemble all parts |
| `POST` | `/videos/multipart/abort` | Cleans up orphaned S3 parts, marks video FAILED |
| `POST` | `/videos/init-upload` | Legacy single-part presigned PUT (small files only) |

### Status & Streaming

| Method | Path | Description |
|---|---|---|
| `GET` | `/videos/{id}/status` | Returns current `VideoStatus` |
| `GET` | `/videos/{id}/stream` | Returns HLS master URL (Redis cache-aside) |
| `GET` | `/videos` | Returns all READY videos for gallery |

### Internal

| Method | Path | Description |
|---|---|---|
| `POST` | `/internal/s3/uploaded` | Lambda webhook — validates `X-Webhook-Secret`, triggers async encoding |

---

## Complete Data Flow: Upload → Playback

```
1. USER DROPS FILE
   └── validateVideoFile() — max 2 GB, MIME must start with "video/"

2. MULTIPART INITIATE
   Client → POST /videos/multipart/initiate {fileName, contentType, partCount}
   Server  → creates Video record (status=REQUESTED) in MySQL
           → calls S3 CreateMultipartUpload → gets uploadId
           → generates N presigned UploadPart URLs (60 min TTL each)
   Client ← {videoId, uploadId, presignedUrls[{partNumber, url}]}

3. PARALLEL S3 UPLOAD (client-direct — server never touches video bytes)
   file.slice() into 5 MB chunks (CHUNK_SIZE = 5 * 1024 * 1024)
   runWithConcurrency(tasks, MAX=6) — sliding window, keeps 6 PUTs in-flight
   (6 matches browser's per-host connection limit)
   Each PUT → presigned S3 URL directly, returns ETag in response header
   Byte-weighted progress: Σ(partBytes × partPercent) / totalBytes → 0–100%
   Progress tracked in plain array (not state) to avoid extra React renders

4. COMPLETE
   Client  → sorts parts by partNumber ASC (S3 assembles in the order you give it —
             chunks upload in parallel so arrival order at S3 is random)
   Client → POST /videos/multipart/complete {videoId, uploadId, parts[{partNumber,etag}]}
   Server  → maps DTOs and forwards to S3 CompleteMultipartUpload as-is
           → S3 assembles object at: s3://bucket/videos/<videoId>/<fileName>
   Client ← 200 OK

5. LAMBDA WEBHOOK (S3 fires ObjectCreated once the object is fully assembled)
   S3 does NOT call your server directly — it fires a native AWS event.
   Lambda bridges the gap: receives the S3 event, translates it into an HTTP webhook.
   S3 event → AWS Lambda
   Lambda  → POST /internal/s3/uploaded
             Header: X-Webhook-Secret: <shared secret>
             Body:   {bucket, key}
   Server  → validates secret (403 on mismatch)
           → extracts videoId from key: "videos/<id>/..." → split("/")[1]
           → sets video status = PROCESSING
           → calls encodeVideo(videoId, s3Key)  [@Async — non-blocking]
   Lambda ← 200 OK immediately

6. ENCODING PIPELINE  (@Async on Spring thread pool)

   a. Create temp dirs:
      {encoding.temp-dir}/{videoId}/{jobUUID}/
        raw_video      ← downloaded source file
        encoded/       ← FFmpeg output per profile

   b. Download: S3 GetObject → raw_video

   c. Probe: ffprobe → width, height, duration, fileSize

   d. Encode per applicable profile (skips profiles where height > source height):
      ┌─────────┬────────────┬───────────┬───────────┬────────────┐
      │ Profile │ Resolution │ Bitrate   │ MaxRate   │ BufferSize │
      ├─────────┼────────────┼───────────┼───────────┼────────────┤
      │ 1080p   │ 1920×1080  │ 5000 kbps │ 5350 kbps │  7500 kbps │
      │  720p   │ 1280×720   │ 2800 kbps │ 2996 kbps │  4200 kbps │
      │  480p   │  854×480   │ 1400 kbps │ 1498 kbps │  2100 kbps │
      │  360p   │  640×360   │  800 kbps │  856 kbps │  1200 kbps │
      └─────────┴────────────┴───────────┴───────────┴────────────┘
      FFmpeg flags:
        -vf scale=W:H
        -c:v libx264 -preset veryfast -crf 23
        -maxrate / -bufsize from profile
        -c:a aac -b:a 128k
        -hls_time 3                   ← 3-second segments
        -hls_list_size 0              ← keep all segments in playlist
        -hls_segment_filename segment_%03d.ts
        -f hls playlist.m3u8
      Output per profile: encoded/{profile}/playlist.m3u8 + segment_NNN.ts

   e. Generate master.m3u8:
      #EXTM3U
      #EXT-X-VERSION:3
      #EXT-X-STREAM-INF:BANDWIDTH=...,RESOLUTION=1280x720,CODECS="avc1.42E01E,mp4a.40.2"
      720p/playlist.m3u8
      ... (one entry per encoded profile)

   f. Upload encoded/ directory to S3: encoded/<videoId>/...
        .m3u8 → Content-Type: application/x-mpegURL
        .ts   → Content-Type: video/MP2T

   g. Build HLS URL using region-specific endpoint:
      https://<bucket>.s3.<region>.amazonaws.com/encoded/<videoId>/master.m3u8
      (NOT s3.amazonaws.com — see CORS note below)

   h. Update DB: status=READY, hlsMasterUrl, width, height, duration, fileSize, processedAt

   i. finally block: cleanup(jobDir) — always runs, deletes temp files recursively
      On error: status=FAILED, failureReason=exception.getMessage()

7. CLIENT POLLS STATUS
   GET /videos/{videoId}/status  every 5 seconds  (useVideoStatus hook)
   Stops polling on READY or FAILED
   Transient network errors set an error message but do NOT stop polling
   readyHandledRef guards against duplicate stream URL fetches on concurrent polls

8. STREAM URL FETCH  (triggered once on READY)
   Client → GET /videos/{videoId}/stream
   Server  → check Redis key "video::hls::<videoId>"
           → MISS: query MySQL → write to Redis (TTL = 3600s) → return
           → HIT: return cached URL (skips MySQL entirely)
   Client ← {videoId, streamingUrl}

9. PLAYBACK  (hls.js)
   Hls.loadSource(streamingUrl) → fetches master.m3u8 from S3
   hls.js picks quality via ABR:
     startLevel: -1                  ← auto from the start
     abrBandWidthUpFactor: 0.7       ← upgrades quality faster than default
     maxBufferLength: 30s
     maxMaxBufferLength: 60s
     maxBufferSize: 60 MB
   Fetches active profile playlist → fetches .ts segments continuously
   ABR switches quality dynamically as bandwidth changes
   Quality badge shown in player via Hls.Events.LEVEL_SWITCHED
   Safari: falls back to native <video src> (Safari has native HLS support)
   Fatal error recovery:
     network error → hls.startLoad()
     media error  → hls.recoverMediaError()
```

---

## Frontend Component Tree

```
app/layout.tsx  (global <Toaster> from sonner — top-center)
│
├── app/page.tsx
│   └── <VideoGallery>
│         useVideos() — GET /videos on mount
│         Responsive CSS grid: 1 / 2 / 3 columns
│         Each card: 16:9 placeholder + play icon overlay
│         Card click → <VideoModal> (full-screen backdrop)
│           <VideoPlayer streamUrl={...} />
│             hls.js instance, ABR quality badge
│             <video crossOrigin="anonymous"> (required for S3 CORS)
│           Closes on: Escape key / backdrop click / close button
│           Body scroll locked while open
│
└── app/upload/page.tsx
    └── <VideoUpload>
          Phase 1 — File selection:
            react-dropzone, accept: video/*, validateVideoFile()
          Phase 2 — In-progress:
            Progress bar (byte-weighted 0–100%), cancel button
            cancel → abortController.abort() + POST /multipart/abort
          Phase 3 — Post-upload:
            <UploadStatus videoId={...}>
              useVideoStatus() — 5s polling
              Status badge (PROCESSING / READY / FAILED)
              Stream preview on READY via <VideoPlayer>
```

---

## Redis Caching

- **Key pattern:** `video::hls::<videoId>`
- **Value:** `StreamingResponse` JSON (videoId + streamingUrl)
- **TTL:** 3600 seconds (1 hour), configurable via `redis-ttl` property
- **Serialization:** `GenericJackson2JsonRedisSerializer`
- The HLS master URL is immutable once written — a long TTL is entirely safe
- Supported backends: local Redis (dev) or Upstash (cloud, requires SSL)

---

## S3 Bucket Layout

```
s3://your-bucket/
├── videos/
│   └── <videoId>/
│       └── <originalFileName>        ← raw uploaded file
└── encoded/
    └── <videoId>/
        ├── master.m3u8               ← HLS master playlist
        ├── 1080p/
        │   ├── playlist.m3u8
        │   ├── segment_000.ts
        │   └── segment_NNN.ts
        ├── 720p/  ...
        ├── 480p/  ...
        └── 360p/  ...
```

---

## Exception Handling

| Exception | HTTP Status |
|---|---|
| `VideoNotFoundException` | 404 Not Found |
| `VideoNotReadyToStreamException` | 409 Conflict |
| `VideoProbeException` | 500 Internal Server Error |
| `EncodingException` | 500 Internal Server Error |
| `MethodArgumentNotValidException` | 400 Bad Request (field error map) |

---

## Key Design Decisions & Gotchas

### Video bytes never pass through Spring Boot
Presigned URLs let the client PUT chunks directly to S3. The server only orchestrates presigned URL generation and metadata. This is correct for large file uploads — avoids memory pressure, timeouts, and bandwidth costs on the server.

### S3 CORS and the 307 redirect trap
The HLS URL is built with the region-specific endpoint (`s3.<region>.amazonaws.com`) instead of the global one (`s3.amazonaws.com`). The global endpoint redirects to the regional one, but browsers follow that redirect **without the `Origin` header** — S3 never sends `Access-Control-Allow-Origin` back — playback silently fails. The `<video crossOrigin="anonymous">` attribute is also required to send an `Origin` header with every `.ts` segment fetch.

### Multipart abort hygiene
Both the server (`abortMultipartUpload`) and client (`cancelUpload`) ensure orphaned S3 parts are deleted on failure or cancel. Incomplete multipart uploads accrue S3 storage charges if not cleaned up.

### Progress tracking avoids extra React renders
Per-part progress values are stored in a plain `number[]` ref (not state). Only the aggregated byte-weighted percentage is written to state, preventing O(partCount) re-renders per progress tick.

### Kafka and WebSocket are not implemented
Both directories (`infrastructure/kafka/`, `infrastructure/websocket/`) exist but are empty. Event delivery is a direct HTTP webhook from Lambda. Status updates use client-side polling (5s), not server push.

### No authentication
The `userId` field is commented out in the `Video` entity. CORS is fully open (`*`). This is a portfolio/demo project.

### Sprite generation is not implemented
The `spriteUrl` column exists in the schema and the services directory is stubbed out, but no sprite generation logic is present.

---

## Configuration Reference

All configuration lives in `server/streamforge/src/main/resources/application.properties` (copy from `.example`):

| Property | Description |
|---|---|
| `spring.datasource.url` | MySQL JDBC URL (`streamforge_db`) |
| `spring.data.redis.*` | Redis host/port/password/SSL |
| `redis-ttl` | HLS URL cache TTL in seconds (default 3600) |
| `aws.bucket-name` | S3 bucket name |
| `aws.region` | Must exactly match bucket's AWS region |
| `aws.access-key` / `aws.secret-key` | IAM credentials |
| `aws.lambda-webhook-secret` | Shared secret validated on `/internal/s3/uploaded` |
| `ffmpeg.path` / `ffprobe.path` | Absolute paths to FFmpeg binaries |
| `encoding.temp-dir` | Local temp directory for encoding jobs |

Client configuration is in `client/.env.local`:

| Variable | Description |
|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | Spring Boot base URL (default `http://localhost:8080/videos`) |
