package com.krakow.theaters.controller;

import com.krakow.theaters.dto.ErrorResponse;
import com.krakow.theaters.dto.ReviewDto;
import com.krakow.theaters.service.ReviewService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping(value = "/play", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ReviewDto>> getPlayReviews(
            @RequestParam String playId,
            @RequestParam(required = false) Long currentUserId) {

        List<ReviewDto> reviews = reviewService.getReviewsByPlayId(playId, currentUserId);
        return ResponseEntity.ok(reviews);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createReview(@RequestBody CreateReviewRequest request) {
        try {
            ReviewDto review = reviewService.createReview(
                    request.getUserId(),
                    request.getPlayId(),
                    request.getContent(),
                    request.getRating(),
                    request.getParentId()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(review);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new ErrorResponse("CREATE_FAILED", e.getMessage())
            );
        }
    }

    @DeleteMapping(value = "/{reviewId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteReview(
            @PathVariable Long reviewId,
            @RequestParam Long userId) {
        try {
            reviewService.deleteReview(reviewId, userId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new ErrorResponse("DELETE_FAILED", e.getMessage())
            );
        }
    }

    @PostMapping(value = "/{reviewId}/like", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> toggleLike(
            @PathVariable Long reviewId,
            @RequestParam Long userId) {
        try {
            ReviewDto review = reviewService.toggleLike(reviewId, userId);
            return ResponseEntity.ok(review);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new ErrorResponse("LIKE_FAILED", e.getMessage())
            );
        }
    }

    @Getter
    @Setter
    public static class CreateReviewRequest {
        private Long userId;
        private String playId;
        private String content;
        private Integer rating;
        private Long parentId;
    }
}
