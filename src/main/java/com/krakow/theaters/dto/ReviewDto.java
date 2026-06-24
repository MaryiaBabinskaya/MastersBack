package com.krakow.theaters.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDto {
    private Long id;
    private Long userId;
    private String userNickname;
    private String userAvatarUrl;
    private String playId;
    private String content;
    private Integer rating;
    private Long parentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer likesCount;
    private Boolean isLikedByCurrentUser;
    private Boolean isAuthor;
    private List<ReviewDto> replies;
}
