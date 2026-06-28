package com.stream_forge.streamforge.services.encoding.service;

import java.io.File;
import java.nio.file.Path;

public interface S3Service {
    public void download(String s3Url, Path target);
    public void uploadDirectory(File rootDir, String prefix);
}
