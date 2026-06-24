package com.krakow.theaters.repository;

import com.krakow.theaters.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByParentIdOrderByCreatedAtAsc(Long parentId);

    @Query("SELECT r FROM Review r WHERE r.playId = :playId AND r.parentId IS NULL ORDER BY r.createdAt DESC")
    List<Review> findTopLevelReviewsByPlayId(@Param("playId") String playId);
}