package com.stream_forge.streamforge.services.encoding.service;

import java.io.File;
import java.nio.file.Path;

public interface S3Service {

    /** Downloads an S3 object to a local file path. */
    void download(String s3Key, Path target);

    /**
     * Recursively uploads an entire local directory to S3.
     * Preserves the relative folder structure under the given prefix.
     * Used for uploading the full HLS encoded output in one call.
     */
    void uploadDirectory(File rootDir, String prefix);

    /**
     * Uploads a single local file to S3 at the given key with the given MIME type.
     * Used for individual file uploads such as thumbnails.
     *
     * @param file        the local file to upload
     * @param s3Key       the destination S3 object key
     * @param contentType the MIME type to set on the S3 object
     */
    void uploadFile(File file, String s3Key, String contentType);
}
