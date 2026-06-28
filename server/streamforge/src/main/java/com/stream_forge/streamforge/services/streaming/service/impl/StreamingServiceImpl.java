package com.stream_forge.streamforge.services.streaming.service.impl;


import com.stream_forge.streamforge.entity.Video;
import com.stream_forge.streamforge.entity.VideoStatus;
import com.stream_forge.streamforge.exception.VideoNotReadyToStreamException;
import com.stream_forge.streamforge.services.streaming.dto.StreamingResponse;
import com.stream_forge.streamforge.services.streaming.service.StreamingService;
import com.stream_forge.streamforge.services.video.service.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class StreamingServiceImpl implements StreamingService {
    private final VideoService videoService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_KEY  = "video::hls::";

    @Value("${redis-ttl}")
    private Long redisTTL;

    @Override
    public StreamingResponse getStreamingUrl(String videoId){
        String key = CACHE_KEY + videoId;

        // 1. Check if redis has this url stored
        String cachedUrl = (String) redisTemplate.opsForValue().get(key);
        if (cachedUrl != null) {
            return new StreamingResponse(videoId, cachedUrl);
        }

        // 2. if not cached -> fetch from DB + cache it + send response
        Video video = videoService.getVideoById(videoId);

        if(video.getStatus() != VideoStatus.READY){
            throw new VideoNotReadyToStreamException("Video not ready to stream. Current Status: " + video.getStatus());
        }

        redisTemplate.opsForValue()
                .set(key, video.getHlsMasterUrl(), Duration.ofSeconds(redisTTL));
        return new StreamingResponse(videoId, video.getHlsMasterUrl());
    }

}
