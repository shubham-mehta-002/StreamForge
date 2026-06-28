package com.stream_forge.streamforge.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "videos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Video {

    @Id
    private String id;

    // Enable: if authentication supported
//    @Column(nullable = false)
//    private Long userId;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false)
    private String s3OriginalKey;

    private String hlsMasterUrl;

    private String thumbnailUrl;

    private String spriteUrl;

    @Enumerated(EnumType.STRING)
    private VideoStatus status;

    private Long duration; // in seconds

    private Long fileSize; // in bytes

    private Integer width;

    private Integer height;

    @Column(length = 1000)
    private String failureReason;

    @Column(updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(updatable = false)
    private LocalDateTime processedAt;
}