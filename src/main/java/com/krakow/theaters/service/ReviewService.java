package com.krakow.theaters.service;

import com.krakow.theaters.dto.ReviewDto;
import com.krakow.theaters.model.Review;
import com.krakow.theaters.model.ReviewLike;
import com.krakow.theaters.model.User;
import com.krakow.theaters.repository.ReviewLikeRepository;
import com.krakow.theaters.repository.ReviewRepository;
import com.krakow.theaters.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ReviewDto> getReviewsByPlayId(String playId, Long currentUserId) {
        log.info("[REVIEW] Fetching reviews for playId: {}, currentUser: {}", playId, currentUserId);
        List<Review> reviews = reviewRepository.findTopLevelReviewsByPlayId(playId);
        return reviews.stream()
                .map(review -> mapToDtoWithReplies(review, currentUserId))
                .toList();
    }

    @Transactional
    public ReviewDto createReview(Long userId, String playId, String content, Integer rating, Long parentId) {
        log.info("[REVIEW] Creating review - userId: {}, playId: {}, rating: {}", userId, playId, rating);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Review review = new Review();
        review.setUser(user);
        review.setPlayId(playId);
        review.setContent(content);
        review.setRating(rating);
        review.setParentId(parentId);

        Review savedReview = reviewRepository.save(review);
        log.info("[REVIEW] Review created successfully - reviewId: {}", savedReview.getId());

        return mapToDto(savedReview, userId);
    }

    @Transactional
    public void deleteReview(Long reviewId, Long userId) {
        log.info("[REVIEW] Deleting review - reviewId: {}, userId: {}", reviewId, userId);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));

        if (!review.getUser().getId().equals(userId)) {
            throw new RuntimeException("You can only delete your own reviews");
        }

        reviewRepository.delete(review);
        log.info("[REVIEW] Review deleted successfully");
    }

    @Transactional
    public ReviewDto toggleLike(Long reviewId, Long userId) {
        log.info("[REVIEW] Toggling like - reviewId: {}, userId: {}", reviewId, userId);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean isLiked = reviewLikeRepository.existsByUserIdAndReviewId(userId, reviewId);

        if (isLiked) {
            reviewLikeRepository.deleteByUserIdAndReviewId(userId, reviewId);
            review.setLikesCount(Math.max(0, review.getLikesCount() - 1));
            log.info("[REVIEW] Unliked review");
        } else {
            ReviewLike reviewLike = new ReviewLike();
            reviewLike.setUser(user);
            reviewLike.setReview(review);
            reviewLikeRepository.save(reviewLike);
            review.setLikesCount(review.getLikesCount() + 1);
            log.info("[REVIEW] Liked review");
        }

        Review savedReview = reviewRepository.save(review);
        return mapToDto(savedReview, userId);
    }

    private ReviewDto mapToDtoWithReplies(Review review, Long currentUserId) {
        ReviewDto dto = mapToDto(review, currentUserId);

        List<Review> replies = reviewRepository.findByParentIdOrderByCreatedAtAsc(review.getId());
        List<ReviewDto> replyDtos = replies.stream()
                .map(reply -> mapToDto(reply, currentUserId))
                .toList();

        dto.setReplies(replyDtos);
        return dto;
    }

    private ReviewDto mapToDto(Review review, Long currentUserId) {
        ReviewDto dto = new ReviewDto();
        dto.setId(review.getId());
        dto.setUserId(review.getUser().getId());
        dto.setUserNickname(review.getUser().getNickname());
        dto.setUserAvatarUrl(review.getUser().getAvatarUrl());
        dto.setPlayId(review.getPlayId());
        dto.setContent(review.getContent());
        dto.setRating(review.getRating());
        dto.setParentId(review.getParentId());
        dto.setCreatedAt(review.getCreatedAt());
        dto.setUpdatedAt(review.getUpdatedAt());
        dto.setLikesCount(review.getLikesCount());

        if (currentUserId != null) {
            dto.setIsLikedByCurrentUser(
                    reviewLikeRepository.existsByUserIdAndReviewId(currentUserId, review.getId())
            );
            dto.setIsAuthor(review.getUser().getId().equals(currentUserId));
        } else {
            dto.setIsLikedByCurrentUser(false);
            dto.setIsAuthor(false);
        }

        return dto;
    }
}