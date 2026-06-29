package com.stream_forge.streamforge.services.video.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Request body sent by the client once all parts have been uploaded to S3.
 *
 * After each individual part PUT succeeds, S3 returns an ETag in the response
 * header. The client collects all { partNumber, etag } pairs and sends them
 * here so the server can call S3's CompleteMultipartUpload API to assemble
 * all parts into the final object.
 */
@Data
public class MultipartCompleteRequest {

    @NotBlank
    private String videoId;

    @NotBlank
    private String uploadId;

    /**
     * Ordered list of part numbers and their ETags as returned by S3.
     * Parts must be listed in ascending partNumber order.
     */
    @NotEmpty
    private List<PartDetail> parts;

    /**
     * Represents one uploaded part — its S3 part number and the ETag
     * value returned by S3 in the response header of the part PUT request.
     */
    @Data
    public static class PartDetail {
        private int partNumber;
        private String etag;
    }
}
