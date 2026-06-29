package com.stream_forge.streamforge.services.video.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for aborting an in-progress multipart upload.
 *
 * This must be called when the user cancels an upload or when a fatal
 * error occurs mid-upload. Without aborting, S3 retains all uploaded
 * parts and continues to charge for their storage.
 */
@Data
public class MultipartAbortRequest {

    @NotBlank
    private String videoId;

    @NotBlank
    private String uploadId;
}
