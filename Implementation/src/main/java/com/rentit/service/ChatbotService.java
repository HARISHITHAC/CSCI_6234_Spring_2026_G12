package com.rentit.service;

import com.rentit.config.ApiException;
import com.rentit.domain.ChatMessage;
import com.rentit.domain.ChatSession;
import com.rentit.domain.UserAccount;
import com.rentit.domain.enums.ListingKind;
import com.rentit.domain.enums.Role;
import com.rentit.dto.ChatDtos;
import com.rentit.dto.ListingDtos;
import com.rentit.repository.ChatMessageRepository;
import com.rentit.repository.ChatSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatbotService {

    private static final Pattern MAX_PRICE_PATTERN = Pattern.compile(
            "(?:under|below|less than|max|up to|upto)\\s*\\$?\\s*(\\d+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("\\$\\s*(\\d+(?:\\.\\d+)?)");
    private static final Set<String> EQUIPMENT_TERMS = Set.of(
            "bike", "bicycle", "car", "vehicle", "camera", "tool", "equipment", "drone", "scooter"
    );
    private static final Set<String> PROPERTY_TERMS = Set.of(
            "loft", "apartment", "studio", "house", "villa", "property", "room", "home"
    );
    private static final Set<String> CONTEXT_STOP_WORDS = Set.of(
            "got", "any", "have", "has", "do", "you", "i", "we", "me", "my", "need", "want",
            "looking", "look", "for", "in", "on", "at", "om", "near", "the", "a", "an", "to",
            "show", "please", "give", "find", "some", "with", "and", "or"
    );

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ListingService listingService;
    private final GeminiRagService geminiRagService;
    private final DtoMapper dtoMapper;

    public ChatbotService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            ListingService listingService,
            GeminiRagService geminiRagService,
            DtoMapper dtoMapper
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.listingService = listingService;
        this.geminiRagService = geminiRagService;
        this.dtoMapper = dtoMapper;
    }

    @Transactional
    public ChatDtos.StartSessionResponse startSession(UserAccount user) {
        ChatSession session = new ChatSession();
        session.setUser(user);
        ChatSession saved = chatSessionRepository.save(session);

        String greeting = "Hi! Tell me location, budget, and what you need. I will suggest listings.";
        saveMessage(saved, "BOT", greeting);
        return new ChatDtos.StartSessionResponse(saved.getId(), saved.getStartedAt(), greeting);
    }

    @Transactional
    public ChatDtos.ChatReplyResponse sendMessage(UserAccount actor, ChatDtos.SendMessageRequest request) {
        ChatSession session = chatSessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chat session not found."));
        boolean isOwner = session.getUser().getId().equals(actor.getId());
        if (!isOwner && actor.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot use this chat session.");
        }

        saveMessage(session, "USER", request.text().trim());

        List<ChatMessage> persistedMessages = chatMessageRepository.findBySessionIdOrderByTimestampAsc(session.getId());
        List<ListingDtos.ListingView> catalog = listingService.allActiveListingViews();

        ChatResult result = geminiRagService.decide(persistedMessages, catalog)
                .map(decision -> applyGeminiDecision(decision, catalog))
                .orElseGet(() -> runRuleBasedFallback(persistedMessages));

        String reply = result.reply();
        List<ListingDtos.ListingView> recommendations = result.recommendations();
        saveMessage(session, "BOT", reply);

        List<ChatDtos.ChatMessageView> history = chatMessageRepository.findBySessionIdOrderByTimestampAsc(session.getId()).stream()
                .map(dtoMapper::toChatMessageView)
                .collect(Collectors.toList());
        return new ChatDtos.ChatReplyResponse(session.getId(), reply, recommendations, history);
    }

    private ChatResult applyGeminiDecision(
            GeminiRagService.GeminiDecision decision,
            List<ListingDtos.ListingView> catalog
    ) {
        GeminiRagService.GeminiFilters filters = decision.filters();
        Map<UUID, ListingDtos.ListingView> byId = new HashMap<>();
        for (ListingDtos.ListingView listing : catalog) {
            byId.put(listing.id(), listing);
        }

        List<ListingDtos.ListingView> recommendations = decision.recommendationIds().stream()
                .map(byId::get)
                .filter(listing -> listing != null && matchesFilters(listing, filters))
                .collect(Collectors.toList());
        if (recommendations.isEmpty()) {
            recommendations = searchByFilters(filters);
        }

        recommendations = recommendations.stream()
                .filter(listing -> matchesFilters(listing, filters))
                .sorted(Comparator.comparing(ListingDtos.ListingView::pricePerDay))
                .limit(5)
                .collect(Collectors.toList());

        String reply = buildReply(
                new ChatIntent(filters.query(), filters.location(), filters.maxPrice(), filters.kind()),
                recommendations
        );
        return new ChatResult(reply, recommendations);
    }

    private ChatResult runRuleBasedFallback(List<ChatMessage> persistedMessages) {
        ChatIntent intent = buildIntent(persistedMessages);
        List<ListingDtos.ListingView> recommendations = searchByIntent(intent);
        String reply = buildReply(intent, recommendations);
        return new ChatResult(reply, recommendations);
    }

    private List<ListingDtos.ListingView> searchByIntent(ChatIntent intent) {
        return listingService.searchListings(
                intent.queryText(),
                intent.location(),
                null,
                intent.maxPrice(),
                intent.kind(),
                null,
                null,
                1
        ).stream()
                .sorted(Comparator.comparing(ListingDtos.ListingView::pricePerDay))
                .limit(5)
                .collect(Collectors.toList());
    }

    private List<ListingDtos.ListingView> searchByFilters(GeminiRagService.GeminiFilters filters) {
        return listingService.searchListings(
                filters.query(),
                filters.location(),
                null,
                filters.maxPrice(),
                filters.kind(),
                null,
                null,
                1
        );
    }

    private boolean matchesFilters(ListingDtos.ListingView listing, GeminiRagService.GeminiFilters filters) {
        if (filters == null) {
            return true;
        }
        if (filters.kind() != null && listing.kind() != filters.kind()) {
            return false;
        }
        if (filters.location() != null && !listing.location().toLowerCase(Locale.ROOT)
                .contains(filters.location().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (filters.maxPrice() != null && listing.pricePerDay().compareTo(filters.maxPrice()) > 0) {
            return false;
        }
        return true;
    }

    private ChatIntent buildIntent(List<ChatMessage> messages) {
        List<String> userTexts = messages.stream()
                .filter(msg -> "USER".equalsIgnoreCase(msg.getSender()))
                .map(ChatMessage::getContent)
                .collect(Collectors.toList());
        String latest = userTexts.isEmpty() ? "" : userTexts.get(userTexts.size() - 1);
        String previous = userTexts.size() > 1 ? userTexts.get(userTexts.size() - 2) : "";
        String combinedUserText = String.join(" ", userTexts);
        String rollingQuery = hasMeaningfulTokens(latest) ? latest : (previous + " " + latest).trim();
        if (rollingQuery.isBlank()) {
            rollingQuery = combinedUserText;
        }

        String location = extractLocation(latest);
        if (location == null) {
            location = extractLocation(combinedUserText);
        }

        BigDecimal maxPrice = extractMaxPrice(latest);

        ListingKind kind = extractKind(latest);
        if (kind == null) {
            kind = extractKind(combinedUserText);
        }

        String queryText = rollingQuery;
        if (location != null) {
            queryText = queryText.replaceAll("(?i)\\b" + Pattern.quote(location) + "\\b", " ");
            queryText = queryText.replaceAll("(?i)\\bin\\b\\s*$", " ");
            queryText = queryText.replaceAll("\\s+", " ").trim();
        }
        if (queryText.isBlank()) {
            queryText = null;
        }

        return new ChatIntent(
                queryText,
                location,
                maxPrice,
                kind
        );
    }

    private String buildReply(ChatIntent intent, List<ListingDtos.ListingView> recommendations) {
        if (recommendations.isEmpty()) {
            if (intent.kind() != null && intent.maxPrice() != null && intent.location() != null) {
                return "No " + intent.kind().name().toLowerCase(Locale.ROOT)
                        + " listings found in " + intent.location()
                        + " under $" + intent.maxPrice().setScale(2, RoundingMode.HALF_UP)
                        + ". Try increasing budget or changing location.";
            }
            return "I couldn't find strong matches yet. Try keywords like bike/loft plus location and budget (example: 'bike in New York under 50').";
        }

        String titles = recommendations.stream()
                .limit(3)
                .map(ListingDtos.ListingView::title)
                .collect(Collectors.joining(", "));

        List<String> filters = new ArrayList<>();
        if (intent.location() != null) {
            filters.add("location: " + intent.location());
        }
        if (intent.maxPrice() != null) {
            filters.add("budget <= $" + intent.maxPrice().setScale(2, RoundingMode.HALF_UP));
        }
        if (intent.kind() != null) {
            filters.add("type: " + intent.kind().name().toLowerCase(Locale.ROOT));
        }
        String filterSummary = filters.isEmpty() ? "" : " (" + String.join(", ", filters) + ")";
        return "I found " + recommendations.size() + " match(es)" + filterSummary + ". Top options: " + titles + ".";
    }

    private BigDecimal extractMaxPrice(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        List<BigDecimal> values = new ArrayList<>();
        Matcher maxMatcher = MAX_PRICE_PATTERN.matcher(text);
        while (maxMatcher.find()) {
            values.add(new BigDecimal(maxMatcher.group(1)));
        }
        if (values.isEmpty()) {
            Matcher currencyMatcher = CURRENCY_PATTERN.matcher(text);
            while (currencyMatcher.find()) {
                values.add(new BigDecimal(currencyMatcher.group(1)));
            }
        }
        return values.stream().min(Comparator.naturalOrder()).orElse(null);
    }

    private String extractLocation(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = normalize(text);
        for (String location : listingService.activeLocations()) {
            String locationNorm = normalize(location);
            if (normalized.contains(locationNorm)) {
                return location;
            }
        }
        return null;
    }

    private ListingKind extractKind(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = normalize(text);
        for (String term : EQUIPMENT_TERMS) {
            if (normalized.contains(term)) {
                return ListingKind.EQUIPMENT;
            }
        }
        for (String term : PROPERTY_TERMS) {
            if (normalized.contains(term)) {
                return ListingKind.PROPERTY;
            }
        }
        return null;
    }

    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    private boolean hasMeaningfulTokens(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        List<String> tokens = List.of(normalize(text).split(" "));
        return tokens.stream().anyMatch(token ->
                token.length() > 2 && !CONTEXT_STOP_WORDS.contains(token) && !token.chars().allMatch(Character::isDigit)
        );
    }

    private void saveMessage(ChatSession session, String sender, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setSession(session);
        msg.setSender(sender);
        msg.setContent(content);
        chatMessageRepository.save(msg);
    }

    private record ChatIntent(
            String queryText,
            String location,
            BigDecimal maxPrice,
            ListingKind kind
    ) {
    }

    private record ChatResult(
            String reply,
            List<ListingDtos.ListingView> recommendations
    ) {
    }
}
