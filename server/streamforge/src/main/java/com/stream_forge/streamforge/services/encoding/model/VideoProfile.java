package com.stream_forge.streamforge.services.encoding.model;

/**
 * Defines one HLS quality tier for encoding.
 *
 * EncodingServiceImpl holds a static list of these profiles (1080p → 360p).
 * For each profile, it checks if the source video is tall enough to justify
 * encoding at that resolution — if not, the profile is skipped (no upscaling).
 *
 * All 6 fields are passed directly to FFmpeg as encoding parameters.
 *
 * @param name          Quality label (e.g. "720p"). Used as the S3 subfolder name
 *                      and as the entry name in the HLS master playlist.
 * @param width         Target width in pixels for -vf scale=W:H
 * @param height        Target height in pixels. Also used to skip profiles that
 *                      exceed the source video's height (can't upscale).
 * @param bitrateKbps   Average target bitrate in kbps (-b:v). Controls typical
 *                      file size and quality — higher = better quality, larger file.
 * @param maxRateKbps   Peak bitrate cap in kbps (-maxrate). Prevents momentary
 *                      spikes (e.g. fast motion scenes) from overwhelming the viewer's buffer.
 * @param bufferSizeKbps Encoder buffer size in kbps (-bufsize). FFmpeg uses this to
 *                      smooth out bitrate spikes. Typically 1.5× maxRate.
 */
public record VideoProfile(
        String name,
        int width,
        int height,
        int bitrateKbps,
        int maxRateKbps,
        int bufferSizeKbps
) {}