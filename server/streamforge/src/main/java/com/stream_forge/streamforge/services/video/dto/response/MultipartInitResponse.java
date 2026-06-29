package com.stream_forge.streamforge.services.video.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response returned after a multipart upload is initiated.
 *
 * Contains:
 * - videoId      : the UUID assigned to this video in the database
 * - uploadId     : the S3 multipart upload ID — required for all subsequent
 *                  part uploads and the final complete/abort call
 * - s3Key        : the S3 object key where the final assembled file will live
 * - presignedUrls: one presigned PUT URL per part, indexed by part number (1-based)
 *                  The client PUTs each chunk directly to its corresponding URL
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultipartInitResponse {
    private String videoId;
    private String uploadId;
    private String s3Key;
    private List<PartPresignedUrl> presignedUrls;

    /**
     * Maps a part number (1-based) to its S3 presigned PUT URL.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartPresignedUrl {
        private int partNumber;
        private String url;
    }
}
