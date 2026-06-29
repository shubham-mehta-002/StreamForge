package com.stream_forge.streamforge.services.encoding.service.impl;

import com.stream_forge.streamforge.entity.VideoStatus;
import com.stream_forge.streamforge.entity.VideoUpdateRequest;
import com.stream_forge.streamforge.exception.EncodingException;
import com.stream_forge.streamforge.services.encoding.model.VideoJobContext;
import com.stream_forge.streamforge.services.encoding.model.VideoMetadata;
import com.stream_forge.streamforge.services.encoding.model.VideoProfile;
import com.stream_forge.streamforge.services.encoding.service.EncodingService;
import com.stream_forge.streamforge.services.encoding.service.FFmpegService;
import com.stream_forge.streamforge.services.encoding.service.S3Service;
import com.stream_forge.streamforge.services.encoding.util.JobContextFactory;
import com.stream_forge.streamforge.services.video.service.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class EncodingServiceImpl implements EncodingService {
    private final S3Service s3;
    private final JobContextFactory contextFactory;
    private final FFmpegService ffmpeg;
    private final VideoService videoService;

    private static final List<VideoProfile> PROFILES = List.of(
            new VideoProfile("1080p", 1920, 1080, 5000, 5350, 7500),
            new VideoProfile("720p", 1280, 720, 2800, 2996, 4200),
            new VideoProfile("480p", 854, 480, 1400, 1498, 2100),
            new VideoProfile("360p", 640, 360, 800, 856, 1200)
    );


    @Value("${aws.bucket-name}")
    private String bucketName;

    @Value("${aws.region}")
    private String awsRegion;

    /**
     * 1. Download raw video from S3
     * 2. Encode to multiple qualities using FFmpeg
     * 3. Generate HLS playlist (.m3u8) for each quality
     * 4. Create master playlist
     * 5. Upload all encoded files back to S3
     */
    @Override
    @Async
    public void encodeVideo(String videoId, String s3Key) {
        log.info("Encoding started for movie : {} ",videoId);

        VideoJobContext ctx = contextFactory.create(videoId);

        log.info("Context ctx...");
        try{
            // Create temporary directories for storing raw and encoded video files
            Files.createDirectories(ctx.jobDir());
            Files.createDirectories(ctx.encodedDir());

            // 1. Download video
            s3.download(s3Key, ctx.inputFile());

            // 2,3. Encode + create HLS
            VideoMetadata meta = ffmpeg.probe(ctx.inputFile());
            for(VideoProfile profile : PROFILES){

                // you can't create higher resolution video from a lower one
                if(profile.height() > meta.height()){
                    continue;
                }

                Path outDir = ctx.encodedDir().resolve(profile.name());
                Files.createDirectories(outDir);

                ffmpeg.encodeHls(ctx.inputFile(), outDir, profile);

                log.info("Encoding for PROFILE {} ", profile.name());
            }

            log.info("Encoding for all profiles completed ....");

            // 4. Create master playlist
            Path master = ctx.encodedDir().resolve("master.m3u8");
            generateMasterPlaylist(master);
            log.info("master generated ....");


            // 5. Upload all encoded files to S3
            s3.uploadDirectory(ctx.encodedDir().toFile(), "encoded/" + videoId + "/");
            log.info("Uploaded to S3");

            String masterPlaylistKey = "encoded/" + videoId + "/master.m3u8";

            // Use region-specific S3 URL to avoid a 307 redirect.
            // The global endpoint (s3.amazonaws.com) causes a redirect to the
            // bucket's actual region. Browsers follow this redirect WITHOUT the
            // Origin header, so S3 never returns Access-Control-Allow-Origin → CORS fails.
            String hlsUrl = "https://" + bucketName + ".s3." + awsRegion + ".amazonaws.com/" + masterPlaylistKey;
            log.info("HLS : {}",hlsUrl);

            // 6. Generate thumbnail — non-fatal: if this fails the video still goes READY
            String thumbnailUrl = generateAndUploadThumbnail(videoId, ctx, meta.duration());

            // 7. Update the video record with all metadata, HLS URL, thumbnail, and READY status
            VideoUpdateRequest request = VideoUpdateRequest.builder()
                    .status(VideoStatus.READY)
                    .hlsMasterUrl(hlsUrl)
                    .thumbnailUrl(thumbnailUrl)   // null if thumbnail generation failed — that's fine
                    .width(meta.width())
                    .height(meta.height())
                    .originalFileName(meta.originalFileName())
                    .duration(meta.duration())
                    .fileSize(meta.fileSize())
                    .processedAt(LocalDateTime.now())
                    .build();

            videoService.updateVideoDetails(videoId,request);

        }catch (Exception e){
            log.error("Encoding failed : {}", e.getMessage());
            VideoUpdateRequest request = VideoUpdateRequest
                        .builder()
                        .status(VideoStatus.FAILED)
                        .failureReason(e.getMessage())
                        .build();

            videoService.updateVideoDetails(videoId, request);

            throw new EncodingException("Error during Encoding process" , e);
        }finally {
            cleanup(ctx.jobDir());
        }
    }


    /**
     * Generates a JPEG thumbnail from the raw video and uploads it to S3.
     *
     * The frame is extracted at 10% of the video duration — this reliably
     * avoids black/empty frames that often appear at the very beginning,
     * while still representing an early, meaningful scene.
     *
     * This method is intentionally non-fatal: any exception is caught and
     * logged as a warning so the encoding pipeline continues and the video
     * is still marked READY even if thumbnail generation fails.
     *
     * @param videoId   the video UUID (used to build the S3 key)
     * @param ctx       the job context holding temp file paths
     * @param duration  the video duration in seconds (used to calculate seek time)
     * @return          the public S3 URL of the uploaded thumbnail,
     *                  or null if generation failed
     */
    private String generateAndUploadThumbnail(String videoId, VideoJobContext ctx, long duration) {
        try {
            // Seek to 10% into the video — avoids black frames at the start
            long seekSeconds = Math.max(1, duration / 10);

            Path thumbnailPath = ctx.jobDir().resolve("thumbnail.jpg");

            ffmpeg.generateThumbnail(ctx.inputFile(), thumbnailPath, seekSeconds);
            log.info("Thumbnail extracted at {}s for videoId={}", seekSeconds, videoId);

            String s3Key = "thumbnails/" + videoId + "/thumbnail.jpg";
            s3.uploadFile(thumbnailPath.toFile(), s3Key, "image/jpeg");

            String thumbnailUrl = "https://" + bucketName + ".s3." + awsRegion + ".amazonaws.com/" + s3Key;
            log.info("Thumbnail uploaded: {}", thumbnailUrl);

            return thumbnailUrl;

        } catch (Exception e) {
            // Non-fatal — log and return null so the video still reaches READY status
            log.warn("Thumbnail generation failed for videoId={} — video will still be marked READY. Reason: {}",
                    videoId, e.getMessage());
            return null;
        }
    }

    private void generateMasterPlaylist(Path masterPath) throws Exception {

        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n");
        sb.append("#EXT-X-VERSION:3\n\n");

        for (VideoProfile p : PROFILES) {

            int bandwidth = (int) (p.bitrateKbps() * 1.1 * 1000);

            sb.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                    .append(bandwidth)
                    .append(",RESOLUTION=")
                    .append(p.width()).append("x").append(p.height())
                    .append(",CODECS=\"avc1.42E01E,mp4a.40.2\"")
                    .append("\n")
                    .append(p.name()).append("/playlist.m3u8\n\n");
        }

        Files.writeString(masterPath, sb.toString(), StandardCharsets.UTF_8);

        /**
         * #EXTM3U
         * #EXT-X-VERSION:3
         *
         * #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
         * 360p/playlist.m3u8
         *
         * #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
         * 720p/playlist.m3u8
         */
    }

    private void cleanup(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            paths
                    .sorted(Comparator.reverseOrder())  // delete files first → then folders
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete: " + p, e);
                        }
                    });

        } catch (IOException e) {
            throw new RuntimeException("Failed to cleanup directory: " + dir, e);
        }
    }
}
