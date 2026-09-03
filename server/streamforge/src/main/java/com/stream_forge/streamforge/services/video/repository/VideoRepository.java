package com.stream_forge.streamforge.services.video.repository;

import com.stream_forge.streamforge.entity.Video;
import com.stream_forge.streamforge.entity.VideoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoRepository extends JpaRepository<Video, String> {

    /** Returns all videos with the given status. Used by the gallery endpoint. */
    List<Video> findByStatus(VideoStatus status);
}
