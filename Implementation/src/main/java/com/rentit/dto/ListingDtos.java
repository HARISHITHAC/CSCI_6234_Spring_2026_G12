package com.rentit.dto;

import com.rentit.domain.enums.ListingKind;
import com.rentit.domain.enums.ListingStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ListingDtos {

    private ListingDtos() {
    }

    public record CreateListingRequest(
            @NotNull ListingKind kind,
            @NotBlank String title,
            @NotBlank String description,
            @NotBlank String location,
            String imageUrl,
            @NotNull @DecimalMin("0.01") BigDecimal pricePerDay,
            @Min(1) int totalQuantity,
            boolean hostApprovalRequired,
            String propertyType,
            Integer maxGuests,
            String equipmentType,
            String conditionText
    ) {
    }

    public record ListingView(
            UUID id,
            ListingKind kind,
            String title,
            String description,
            BigDecimal pricePerDay,
            String location,
            String imageUrl,
            ListingStatus status,
            boolean hostApprovalRequired,
            int totalQuantity,
            UUID ownerId,
            String ownerName,
            String propertyType,
            Integer maxGuests,
            String equipmentType,
            String conditionText,
            boolean availableForDates
    ) {
    }

    public record AvailabilityUpdateRequest(
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate
    ) {
    }

    public record AvailabilitySlotView(
            UUID id,
            LocalDate startDate,
            LocalDate endDate,
            boolean blocked
    ) {
    }

    public record SearchResponse(
            List<ListingView> listings
    ) {
    }
}
