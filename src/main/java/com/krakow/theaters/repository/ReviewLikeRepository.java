package com.krakow.theaters.repository;

import com.krakow.theaters.model.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {

    boolean existsByUserIdAndReviewId(Long userId, Long reviewId);

    void deleteByUserIdAndReviewId(Long userId, Long reviewId);
}