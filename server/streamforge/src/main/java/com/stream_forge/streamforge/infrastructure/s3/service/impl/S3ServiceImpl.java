package com.stream_forge.streamforge.infrastructure.s3.service.impl;

import com.stream_forge.streamforge.infrastructure.s3.service.S3Service;
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

    // Uploads an entire encoded video folder (HLS output) to S3 while preserving folder structure.
    // prefix -> S3 folder path e.g. "encoded/<videoId>/"
    @Override
    public void uploadDirectory(File rootDir, String prefix) {
        uploadRecursive(rootDir, rootDir, prefix);
    }

    /**
     * Uploads a single file to S3 at the given key with the given content type.
     */
    @Override
    public void uploadFile(File file, String s3Key, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromFile(file)
        );
        log.info("Uploaded single file to S3: {}", s3Key);
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

        // HLS output contains two types of files:
        // - .m3u8 → playlist file (tells the player what segments to load and in what order)
        // - .ts   → actual video segment (3 seconds of video data)
        // S3 stores the Content-Type and sends it back when the browser requests the file.
        // The HLS player uses it to know how to handle each file.
        // If we don't set the correct type, some browsers will refuse to play the video.
        String contentType = file.getName().endsWith(".m3u8")
                ? "application/x-mpegURL"   // playlist file
                : "video/MP2T";             // video segment

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
