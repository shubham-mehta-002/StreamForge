package com.stream_forge.streamforge.services.video.service;

import com.stream_forge.streamforge.entity.Video;
import com.stream_forge.streamforge.entity.VideoStatus;
import com.stream_forge.streamforge.entity.VideoUpdateRequest;
import com.stream_forge.streamforge.services.video.dto.request.InitUploadRequest;
import com.stream_forge.streamforge.services.video.dto.response.InitUploadResponse;
import com.stream_forge.streamforge.services.video.dto.response.VideoStatusResponse;
import com.stream_forge.streamforge.services.video.dto.response.VideoSummaryResponse;

import java.util.List;

public interface VideoService {
    public InitUploadResponse createVideo(InitUploadRequest request);
    public Video getVideoById(String videoId);
    public void updateVideoStatus(String videoId, VideoStatus status);
    public void updateVideoDetails(String videoId, VideoUpdateRequest request);
    public VideoStatusResponse getVideoStatus(String videoId);
    public List<VideoSummaryResponse> getAllVideos();
}

