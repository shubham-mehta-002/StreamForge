package com.stream_forge.streamforge.services.encoding.service.impl;

import com.stream_forge.streamforge.services.encoding.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class S3ServiceImpl implements S3Service {
    private final S3Client s3Client;

    @Value("${aws.bucket-name}")
    private String bucketName;

    @Override
    public void download(String s3Url, Path target) {
        log.info("Download started");

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Url)
                .build();
        s3Client.getObject(request, target);
        log.info("Download completed");
    }

    // It uploads an entire encoded video folder (HLS output) to S3 while preserving folder structure.
    // prefix -> S3 folder path
    @Override
    public void uploadDirectory(File rootDir, String prefix) {
        uploadRecursive(rootDir, rootDir, prefix);
    }

    private void uploadRecursive(File root, File file, String prefix) {

        if (file.isDirectory()) {
            for (File f : Objects.requireNonNull(file.listFiles())) {
                uploadRecursive(root, f, prefix);
            }
            return;
        }

        String relative = root.toPath()
                .relativize(file.toPath())
                .toString()
                .replace("\\", "/");

        String key = prefix + relative;

        String contentType = file.getName().endsWith(".m3u8")
                ? "application/x-mpegURL"
                : "video/MP2T";

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromFile(file)
        );

        log.debug("Uploaded {}", key);
    }

}