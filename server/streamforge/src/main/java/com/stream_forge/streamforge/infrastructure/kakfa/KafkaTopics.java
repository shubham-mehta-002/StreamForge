package com.stream_forge.streamforge.infrastructure.kakfa;

public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String VIDEO_UPLOADED = "video.uploaded";

    public static final String VIDEO_ENCODING_STARTED =
            "video.encoding.started";

    public static final String VIDEO_ENCODING_PROGRESS =
            "video.encoding.progress";

    public static final String VIDEO_ENCODING_COMPLETED =
            "video.encoding.completed";

    public static final String VIDEO_ENCODING_FAILED =
            "video.encoding.failed";
}