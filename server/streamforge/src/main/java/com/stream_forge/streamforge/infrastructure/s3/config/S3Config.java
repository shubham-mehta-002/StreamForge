package com.stream_forge.streamforge.infrastructure.s3.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    @Value("${aws.secret-key}")
    private String secretKey;

    @Value("${aws.access-key}")
    private String accessKey;

    @Value("${aws.region}")
    private String region;

    /**
     * S3Client used for API operations: CreateMultipartUpload,
     * CompleteMultipartUpload, AbortMultipartUpload, GetObject, PutObject.
     *
     * The region here must exactly match the bucket's actual region.
     * A mismatch causes a 307 redirect to the correct region — the browser
     * follows this redirect but loses CORS response headers, breaking playback.
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .build();
    }

    /**
     * S3Presigner used to generate presigned PUT URLs for direct client uploads
     * and presigned UploadPart URLs for multipart uploads.
     */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .build();
    }
}
