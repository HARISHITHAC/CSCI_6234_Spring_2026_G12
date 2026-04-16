package com.rentit.service;

import com.rentit.domain.UserAccount;
import com.rentit.dto.ChatDtos;
import com.rentit.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ChatbotServiceIntegrationTest {

    @Autowired
    private ChatbotService chatbotService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Test
    void shouldRecommendBikeForConversationalPrompt() {
        UserAccount renter = userAccountRepository.findByEmailIgnoreCase("renter@rentit.local").orElseThrow();
        ChatDtos.StartSessionResponse session = chatbotService.startSession(renter);

        ChatDtos.ChatReplyResponse response = chatbotService.sendMessage(
                renter,
                new ChatDtos.SendMessageRequest(session.sessionId(), "got any bikes?")
        );

        assertThat(response.recommendations()).isNotEmpty();
        assertThat(response.recommendations().stream().anyMatch(listing ->
                listing.title().toLowerCase(Locale.ROOT).contains("bike")
                        || (listing.equipmentType() != null
                        && listing.equipmentType().toLowerCase(Locale.ROOT).contains("bike"))
        )).isTrue();
    }

    @Test
    void shouldRecommendLoftForPluralPrompt() {
        UserAccount renter = userAccountRepository.findByEmailIgnoreCase("renter@rentit.local").orElseThrow();
        ChatDtos.StartSessionResponse session = chatbotService.startSession(renter);

        ChatDtos.ChatReplyResponse response = chatbotService.sendMessage(
                renter,
                new ChatDtos.SendMessageRequest(session.sessionId(), "got any lofts?")
        );

        assertThat(response.recommendations()).isNotEmpty();
        assertThat(response.recommendations().stream().anyMatch(listing ->
                listing.title().toLowerCase(Locale.ROOT).contains("loft")
                        || (listing.propertyType() != null
                        && listing.propertyType().toLowerCase(Locale.ROOT).contains("loft"))
        )).isTrue();
    }

    @Test
    void shouldHandleMinorTyposAndLocationHints() {
        UserAccount renter = userAccountRepository.findByEmailIgnoreCase("renter@rentit.local").orElseThrow();
        ChatDtos.StartSessionResponse session = chatbotService.startSession(renter);

        ChatDtos.ChatReplyResponse response = chatbotService.sendMessage(
                renter,
                new ChatDtos.SendMessageRequest(session.sessionId(), "got any Mountain Bike om new york?")
        );

        assertThat(response.recommendations()).isNotEmpty();
        assertThat(response.recommendations().stream().anyMatch(listing ->
                listing.location().equalsIgnoreCase("new york")
        )).isTrue();
        assertThat(response.recommendations().stream().anyMatch(listing ->
                listing.title().toLowerCase(Locale.ROOT).contains("bike")
                        || (listing.equipmentType() != null
                        && listing.equipmentType().toLowerCase(Locale.ROOT).contains("bike"))
        )).isTrue();
    }

    @Test
    void shouldNotReturnEquipmentWhenUserExplicitlyAsksForLoftsAfterBudgetPrompt() {
        UserAccount renter = userAccountRepository.findByEmailIgnoreCase("renter@rentit.local").orElseThrow();
        ChatDtos.StartSessionResponse session = chatbotService.startSession(renter);

        chatbotService.sendMessage(
                renter,
                new ChatDtos.SendMessageRequest(session.sessionId(), "need equipment under $50")
        );
        ChatDtos.ChatReplyResponse response = chatbotService.sendMessage(
                renter,
                new ChatDtos.SendMessageRequest(session.sessionId(), "show me lofts in new york")
        );

        assertThat(response.recommendations()).isNotEmpty();
        assertThat(response.recommendations().stream().allMatch(listing -> listing.kind().name().equals("PROPERTY"))).isTrue();
        assertThat(response.recommendations().stream().anyMatch(listing ->
                listing.title().toLowerCase(Locale.ROOT).contains("loft")
        )).isTrue();
    }
}
