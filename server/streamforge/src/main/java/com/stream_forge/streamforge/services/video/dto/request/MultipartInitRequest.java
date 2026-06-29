package com.stream_forge.streamforge.services.video.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for initiating a multipart upload.
 *
 * The client sends the file name, MIME type, and the total number of parts
 * it plans to upload. The server uses partCount to generate one presigned URL
 * per part, which the client then uses to PUT each chunk directly to S3.
 */
@Data
public class MultipartInitRequest {

    @NotBlank
    private String fileName;

    @NotBlank
    private String contentType;

    /**
     * Number of chunks the client has split the file into.
     * Each part must be >= 5 MB except the last one (S3 minimum part size).
     */
    private int partCount;
}
