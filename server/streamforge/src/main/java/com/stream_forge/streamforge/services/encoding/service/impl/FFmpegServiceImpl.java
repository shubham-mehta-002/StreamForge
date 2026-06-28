package com.stream_forge.streamforge.services.encoding.service.impl;

import com.stream_forge.streamforge.exception.VideoProbeException;
import com.stream_forge.streamforge.services.encoding.model.VideoMetadata;
import com.stream_forge.streamforge.services.encoding.model.VideoProfile;
import com.stream_forge.streamforge.services.encoding.service.FFmpegService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class FFmpegServiceImpl implements FFmpegService {
    @Value("${ffmpeg.path}")
    private String ffmpegPath;

    @Value("${ffprobe.path}")
    private String ffprobePath;

    @Override
    public VideoMetadata probe(Path input) {
        try {
//            ProcessBuilder pb = new ProcessBuilder(
//                    ffprobePath,                     // launch ffprobe from ffmpeg suite
//                    "-v", "error",                           // to show only the error info and not other info like configurations,...
//                    "-select_streams", "v:0",                // media file can contain multiple streams -> video,audio,subtitle : choose only 0th indexed
//                    "-show_entries", "stream=width,height",  // only give height and width as metadata and no other metadata info required like codec, fps, duration
//                    "-of", "csv=p=0",                        // format output as csv eg: 1920x1080 => 1920,1080 (easier to parse)
//                    input.toString()                         // convert the input from Path object to String
//            );
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

            String result = new String(p.getInputStream().readAllBytes()); // convert raw byte -> String format for readability
            // readAllBytes()-> blocks till the process completes

            int code = p.waitFor(); // wait till process exits

            if (code != 0) {
                throw new VideoProbeException("ffprobe failed with exit code " + code);
            }

            String[] lines = result.trim().split("\\R");

            Integer width = Integer.parseInt(lines[0]);
            Integer height = Integer.parseInt(lines[1]);

            Long duration = Math.round(
                    Double.parseDouble(lines[2])
            );

            String originalFileName = input.getFileName().toString();
            String contentType = Files.probeContentType(input);
            Long fileSize = Files.size(input);

            return new VideoMetadata(
                    originalFileName,
                    fileSize,
                    contentType,
                    duration,
                    width,
                    height
            );

        } catch (Exception e) {
            throw new VideoProbeException("ffprobe failed", e);
        }
    }

    @Override
    public void encodeHls(Path input, Path outputDir, VideoProfile profile) throws Exception {

        Path playlist = outputDir.resolve("playlist.m3u8");
        String segmentPattern = outputDir.resolve("segment_%03d.ts").toString();

        List<String> cmd = List.of(
                ffmpegPath,
                "-i", input.toString(),                               // give raw video as input

                "-vf", "scale=" + profile.width() + ":" + profile.height(), // resizing video resolution

                "-c:v", "libx264",                                  // codec : H.264 encoder
                "-preset", "veryfast",                                // encoding speed vs quality tradeoff:
                "-crf", "23",                                       // Constant Rate Factor ie Quality : lower means higher quality (this case : balanced)

                "-maxrate", profile.maxRateKbps() + "k",            // video doesn’t exceed bitrate limits
                "-bufsize", profile.bufferSizeKbps() + "k",

                "-c:a", "aac",                                      // AAC audio codec
                "-b:a", "128k",                                     // 128 kbps audio bitrate

                "-hls_time", "3",                                   // Each video segment = 3 seconds
                "-hls_list_size", "0",                              // Keep ALL segments in playlist (no trimming)
                "-hls_segment_filename", segmentPattern,            // Where to save segments
                "-f", "hls",                                        // Output format = HLS (HTTP Live Streaming)

                playlist.toString()                                 // final output file path
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.inheritIO();

        Process process = pb.start();

        // Captures FFmpeg output (progress + warnings + errors)
        String logs = new String(process.getInputStream().readAllBytes());

        int exit = process.waitFor();

        if (exit != 0) {
            throw new VideoProbeException("FFmpeg failed:\n" + logs);
        }
    }
}
