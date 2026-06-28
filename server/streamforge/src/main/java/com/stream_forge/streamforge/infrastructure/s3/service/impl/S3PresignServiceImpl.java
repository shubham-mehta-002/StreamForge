package com.stream_forge.streamforge.infrastructure.s3.service.impl;

import com.stream_forge.streamforge.infrastructure.s3.service.S3PresignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class S3PresignServiceImpl implements S3PresignService {
    private final S3Presigner s3Presigner;

    @Value("${aws.bucket-name}")
    private String bucketName;

    @Value("${aws.presigned-hls-playlist-url-expiry}")
    private long presignedHlsPlaylistUrlExpiry;

    public String generatePresignedUrl(String s3Key, int expiryInMinutes) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
//                .contentType("video/mp4") // optional
                .build();

        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(expiryInMinutes))
                        .putObjectRequest(putObjectRequest)
                        .build();

        PresignedPutObjectRequest presignedRequest =
                s3Presigner.presignPutObject(presignRequest);

        return presignedRequest.url().toString();
    }
}