package com.rentit.dto;

import com.rentit.domain.enums.BookingStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BookingDtos {

    private BookingDtos() {
    }

    public record CreateBookingRequest(
            @NotNull UUID listingId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Min(1) int quantity
    ) {
    }

    public record BookingView(
            UUID id,
            UUID listingId,
            String listingTitle,
            UUID renterId,
            LocalDate startDate,
            LocalDate endDate,
            int quantity,
            BigDecimal totalPrice,
            BookingStatus status,
            Instant createdAt,
            String rejectionReason,
            boolean paymentRequired,
            String receiptNumber
    ) {
    }

    public record BookingCreateResponse(
            boolean created,
            String message,
            BookingView booking,
            List<ListingDtos.ListingView> alternatives
    ) {
    }

    public record BookingDecisionRequest(
            boolean approve,
            String reason
    ) {
    }
}
