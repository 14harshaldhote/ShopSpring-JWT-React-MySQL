package com.shopeefy.review;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.shopeefy.security.CurrentUser;

@RestController
@Validated
public class ReviewController {

    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @GetMapping("/api/reviews/product/{productId}")
    List<ReviewService.ReviewDto> reviews(@PathVariable @Min(1) long productId) {
        return service.forProduct(productId);
    }

    @GetMapping("/api/ratings/product/{productId}")
    ReviewService.RatingSummary ratings(@PathVariable @Min(1) long productId) {
        return service.summary(productId);
    }

    @PostMapping("/api/reviews/create")
    ResponseEntity<ReviewService.ReviewDto> review(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReviewRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(CurrentUser.id(jwt), req.productId(), req.review()));
    }

    @PostMapping("/api/ratings/create")
    ResponseEntity<ReviewService.RatingSummary> rate(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RatingRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.rate(CurrentUser.id(jwt), req.productId(), req.rating()));
    }

    public record ReviewRequest(@NotNull @Min(1) Long productId, @NotBlank @Size(max = 1000) String review) {
    }

    public record RatingRequest(@NotNull @Min(1) Long productId, @NotNull @Min(1) @Max(5) Integer rating) {
    }
}
