package com.rentit.dto;

import com.rentit.domain.enums.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record PayRequest(
            @NotNull UUID bookingId,
            @NotBlank String method
    ) {
    }

    public record PaymentView(
            UUID id,
            UUID bookingId,
            BigDecimal amount,
            String method,
            PaymentStatus status,
            Instant paidAt,
            String receiptNumber
    ) {
    }
}
