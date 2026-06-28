package com.stream_forge.streamforge.entity;

public enum VideoStatus {
    REQUESTED, // when client request for presigned Url
    PROCESSING, // when lambda webhook trigger encoding service
    READY,  // HLS Url becomes available
    FAILED  // Encoding failed
}