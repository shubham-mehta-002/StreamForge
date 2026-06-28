package com.stream_forge.streamforge.services.video.controller;

import com.stream_forge.streamforge.services.streaming.dto.StreamingResponse;
import com.stream_forge.streamforge.services.streaming.service.StreamingService;
import com.stream_forge.streamforge.services.video.dto.request.InitUploadRequest;
import com.stream_forge.streamforge.services.video.dto.response.InitUploadResponse;
import com.stream_forge.streamforge.services.video.dto.response.VideoStatusResponse;
import com.stream_forge.streamforge.services.video.dto.response.VideoSummaryResponse;
import com.stream_forge.streamforge.services.video.service.VideoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/videos")
@Slf4j
@RequiredArgsConstructor
public class VideoUploadController {
    private final VideoService videoService;
    private final StreamingService streamingService;

    @PostMapping("/init-upload")
    public ResponseEntity<InitUploadResponse> initUpload(
            @Valid @RequestBody InitUploadRequest request
    ) {
        InitUploadResponse response = videoService.createVideo(request);
        return ResponseEntity.ok( response );
    }

    @GetMapping("/{videoId}/stream")
    public ResponseEntity<StreamingResponse> getHLSUrl(@PathVariable String videoId){
        return ResponseEntity.ok(streamingService.getStreamingUrl(videoId));
    }

    @GetMapping("/{videoId}/status")
    public ResponseEntity<VideoStatusResponse> getVideoStatus(@PathVariable String videoId) {
        return ResponseEntity.ok(videoService.getVideoStatus(videoId));
    }

    @GetMapping
    public ResponseEntity<List<VideoSummaryResponse>> getAllVideos() {
        return ResponseEntity.ok(videoService.getAllVideos());
    }

}
