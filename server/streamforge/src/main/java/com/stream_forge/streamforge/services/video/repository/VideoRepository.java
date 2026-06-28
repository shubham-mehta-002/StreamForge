package com.stream_forge.streamforge.services.video.repository;

import com.stream_forge.streamforge.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoRepository extends JpaRepository<Video,String> {
}
