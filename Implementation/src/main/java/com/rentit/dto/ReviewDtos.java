package com.rentit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    public record CreateReviewRequest(
            @NotNull UUID bookingId,
            @Min(1) @Max(5) int rating,
            @NotBlank String comment
    ) {
    }

    public record ReviewView(
            UUID id,
            UUID bookingId,
            UUID listingId,
            UUID authorId,
            String authorName,
            int rating,
            String comment,
            Instant createdAt
    ) {
    }
}
