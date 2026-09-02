package com.stream_forge.streamforge.services.encoding.service.impl;

import com.stream_forge.streamforge.exception.VideoProbeException;
import com.stream_forge.streamforge.services.encoding.model.VideoMetadata;
import com.stream_forge.streamforge.services.encoding.model.VideoProfile;
import com.stream_forge.streamforge.services.encoding.service.FFmpegService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class FFmpegServiceImpl implements FFmpegService {

    @Value("${ffmpeg.path}")
    private String ffmpegPath;

    @Value("${ffprobe.path}")
    private String ffprobePath;

    // ─────────────────────────────────────────────────────────────────────
    // Probe
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Runs ffprobe on the given video file to extract:
     *   - width and height of the first video stream
     *   - total duration (seconds)
     *   - file size (bytes)
     *   - original filename and detected MIME type
     *
     * Output format: default=noprint_wrappers=1:nokey=1
     * produces one value per line: width, height, duration (in that order).
     */
    @Override
    public VideoMetadata probe(Path input) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffprobePath,
                    "-v", "error",
                    "-select_streams", "v:0",
                    "-show_entries", "stream=width,height",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    input.toString()
            );

            Process p = pb.start();

            // readAllBytes() blocks until the process finishes
            String result = new String(p.getInputStream().readAllBytes());

            int code = p.waitFor();
            if (code != 0) {
                throw new VideoProbeException("ffprobe exited with code " + code);
            }

            String[] lines = result.trim().split("\\R");

            Integer width    = Integer.parseInt(lines[0]);
            Integer height   = Integer.parseInt(lines[1]);
            Long duration    = Math.round(Double.parseDouble(lines[2]));

            String originalFileName = input.getFileName().toString();
            String contentType      = Files.probeContentType(input);
            Long fileSize           = Files.size(input);

            return new VideoMetadata(originalFileName, fileSize, contentType, duration, width, height);

        } catch (Exception e) {
            throw new VideoProbeException("ffprobe failed", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // HLS encoding
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Transcodes the input video to HLS format for the given quality profile.
     *
     * Key flags:
     *   -vf scale=W:H         resize to the profile resolution
     *   -c:v libx264           H.264 video codec
     *   -preset veryfast       fast encoding, acceptable quality
     *   -crf 23                constant rate factor (lower = better quality)
     *   -maxrate / -bufsize    cap bitrate to profile limits
     *   -c:a aac -b:a 128k     AAC audio at 128 kbps
     *   -hls_time 3            3-second segments
     *   -hls_list_size 0       keep all segments in the playlist (no window trimming)
     *   -hls_segment_filename  where to write each .ts segment
     *   -f hls                 output as HLS
     */
    @Override
    public void encodeHls(Path input, Path outputDir, VideoProfile profile) throws Exception {
        Path playlist       = outputDir.resolve("playlist.m3u8");
        String segmentPattern = outputDir.resolve("segment_%03d.ts").toString();

        List<String> cmd = List.of(
                ffmpegPath,
                "-i",        input.toString(),
                "-vf",       "scale=" + profile.width() + ":" + profile.height(),
                "-c:v",      "libx264",
                "-preset",   "veryfast",
                "-crf",      "23",
                "-maxrate",  profile.maxRateKbps() + "k",
                "-bufsize",  profile.bufferSizeKbps() + "k",
                "-threads",  "2",    // cap thread count — x264 pre-allocates per-thread frame buffers;
                "-refs",     "1",    // unbound threads on a large source (e.g. 1080p MJPEG) causes OOM
                "-c:a",      "aac",
                "-b:a",      "128k",
                "-hls_time", "3",
                "-hls_list_size", "0",
                "-hls_segment_filename", segmentPattern,
                "-f",        "hls",
                playlist.toString()
        );

        Path logFile = outputDir.resolve("ffmpeg_encode.log");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectOutput(logFile.toFile());  // write to file — avoids pipe-buffer deadlock

        Process process = pb.start();
        int exit = process.waitFor();

        String logs = Files.exists(logFile) ? Files.readString(logFile) : "(no output)";

        if (exit != 0) {
            throw new VideoProbeException("FFmpeg HLS encoding failed (exit=" + exit + "):\n" + logs);
        }
    }
}
