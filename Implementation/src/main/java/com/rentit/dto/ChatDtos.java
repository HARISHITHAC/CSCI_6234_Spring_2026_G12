package com.rentit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record StartSessionResponse(
            UUID sessionId,
            Instant startedAt,
            String greeting
    ) {
    }

    public record SendMessageRequest(
            @NotNull UUID sessionId,
            @NotBlank String text
    ) {
    }

    public record ChatMessageView(
            UUID id,
            String sender,
            String content,
            Instant timestamp
    ) {
    }

    public record ChatReplyResponse(
            UUID sessionId,
            String reply,
            List<ListingDtos.ListingView> recommendations,
            List<ChatMessageView> history
    ) {
    }
}
