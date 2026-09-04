package com.stream_forge.streamforge.services.upload.dto;

import lombok.Data;

/**
 * Payload sent by AWS Lambda after it receives an S3 ObjectCreated event.
 *
 * S3 fires a raw event (Records[].s3.bucket / .object.key) to Lambda.
 * Lambda extracts just these two fields and POSTs them here — acting as
 * a translator between the verbose AWS event format and this clean DTO.
 *
 * Note: the raw S3 event also contains object.size (bytes) — Lambda
 * currently discards it. Forward it here if you want server-side size validation.
 */
@Data
public class S3UploadEventDto {
    private String bucket;  // which S3 bucket the object landed in
    private String key;     // full object key e.g. "videos/<videoId>/filename.mp4"
}