package com.stream_forge.streamforge.services.upload.controller;

import com.stream_forge.streamforge.services.upload.dto.S3UploadEventDto;
import com.stream_forge.streamforge.services.upload.service.UploadProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/s3")
@RequiredArgsConstructor
public class S3WebhookController {
    private final UploadProcessingService uploadProcessingService;

    @Value("${aws.lambda-webhook-secret}")
    private String webhookSecret;


    @PostMapping("/uploaded")
    public ResponseEntity<Void> handleUpload(
            @RequestHeader("X-Webhook-Secret") String secret,
            @RequestBody S3UploadEventDto event) {

        if (!secret.equals(webhookSecret)) {
            return ResponseEntity.status(403).build();
        }
        uploadProcessingService.processUploadedVideo(
                event.getBucket(),
                event.getKey()
        );

        return ResponseEntity.ok().build();
    }
}