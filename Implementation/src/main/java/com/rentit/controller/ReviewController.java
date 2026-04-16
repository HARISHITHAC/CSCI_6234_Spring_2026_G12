package com.rentit.controller;

import com.rentit.domain.UserAccount;
import com.rentit.dto.ReviewDtos;
import com.rentit.service.CurrentUserService;
import com.rentit.service.ReviewService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUserService currentUserService;

    public ReviewController(ReviewService reviewService, CurrentUserService currentUserService) {
        this.reviewService = reviewService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ReviewDtos.ReviewView createReview(
            @Valid @RequestBody ReviewDtos.CreateReviewRequest request,
            HttpSession session
    ) {
        UserAccount actor = currentUserService.requireUser(session);
        return reviewService.createReview(actor, request);
    }

    @GetMapping("/listing/{listingId}")
    public List<ReviewDtos.ReviewView> listingReviews(@PathVariable UUID listingId) {
        return reviewService.listingReviews(listingId);
    }
}
