package com.rentit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentit.config.AiProperties;
import com.rentit.domain.ChatMessage;
import com.rentit.domain.enums.ListingKind;
import com.rentit.dto.ListingDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GeminiRagService {

    private static final Logger log = LoggerFactory.getLogger(GeminiRagService.class);

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    public GeminiRagService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper, AiProperties aiProperties) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.aiProperties = aiProperties;
    }

    public Optional<GeminiDecision> decide(
            List<ChatMessage> history,
            List<ListingDtos.ListingView> activeListings
    ) {
        if (!aiProperties.isGeminiProvider()) {
            return Optional.empty();
        }
        String apiKey = aiProperties.getGemini().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        if (activeListings.isEmpty()) {
            return Optional.empty();
        }

        try {
            String prompt = buildPrompt(history, activeListings);
            String model = aiProperties.getGemini().getModel();
            String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            String path = "/v1beta/models/" + model + ":generateContent?key=" + encodedApiKey;

            RestClient client = restClientBuilder
                    .requestFactory(buildRequestFactory())
                    .baseUrl(aiProperties.getGemini().getBaseUrl())
                    .build();

            Map<String, Object> body = Map.of(
                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", List.of(Map.of("text", prompt))
                            )
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0.15,
                            "responseMimeType", "application/json"
                    )
            );

            JsonNode response = client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                return Optional.empty();
            }
            String text = response.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText("");
            if (text.isBlank()) {
                return Optional.empty();
            }
            JsonNode decisionNode = parseJsonPayload(text);
            GeminiDecision decision = fromJson(decisionNode, activeListings);
            return Optional.of(decision);
        } catch (Exception ex) {
            log.warn("Gemini decision failed, falling back to rules: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private String buildPrompt(List<ChatMessage> history, List<ListingDtos.ListingView> listings) {
        List<Map<String, String>> compactHistory = history.stream()
                .sorted(Comparator.comparing(ChatMessage::getTimestamp))
                .map(msg -> Map.of(
                        "sender", msg.getSender(),
                        "text", msg.getContent()
                ))
                .collect(Collectors.toList());

        List<Map<String, Object>> catalog = listings.stream()
                .map(listing -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", listing.id().toString());
                    row.put("title", listing.title());
                    row.put("kind", listing.kind().name());
                    row.put("location", listing.location());
                    row.put("pricePerDay", listing.pricePerDay());
                    row.put("description", listing.description());
                    row.put("propertyType", listing.propertyType());
                    row.put("equipmentType", listing.equipmentType());
                    row.put("conditionText", listing.conditionText());
                    return row;
                })
                .collect(Collectors.toList());

        return """
                You are RentIt's AI concierge using RAG over the provided catalog.
                Critical rules:
                1) Use ONLY listings from catalog.
                2) Latest user message has highest priority. Do not carry old budget/type unless explicitly repeated.
                3) Never recommend a different type than requested (e.g. loft/property request must not return equipment).
                4) If budget eliminates all options, keep recommendationIds empty and explain that constraint.
                5) Output JSON only, no markdown.

                Required JSON schema:
                {
                  "reply":"string",
                  "filters":{
                    "query":"string|null",
                    "location":"string|null",
                    "kind":"PROPERTY|EQUIPMENT|null",
                    "maxPrice":number|null
                  },
                  "recommendationIds":["uuid", "..."]
                }

                Conversation history:
                %s

                Listing catalog:
                %s
                """.formatted(toJson(compactHistory), toJson(catalog));
    }

    private GeminiDecision fromJson(JsonNode node, List<ListingDtos.ListingView> catalog) {
        String reply = node.path("reply").asText("");
        JsonNode filtersNode = node.path("filters");
        String query = textOrNull(filtersNode.path("query"));
        String location = textOrNull(filtersNode.path("location"));

        ListingKind kind = null;
        String kindRaw = textOrNull(filtersNode.path("kind"));
        if (kindRaw != null) {
            try {
                kind = ListingKind.valueOf(kindRaw.trim().toUpperCase());
            } catch (Exception ignored) {
                kind = null;
            }
        }

        BigDecimal maxPrice = null;
        if (!filtersNode.path("maxPrice").isMissingNode() && !filtersNode.path("maxPrice").isNull()) {
            try {
                maxPrice = new BigDecimal(filtersNode.path("maxPrice").asText());
            } catch (Exception ignored) {
                maxPrice = null;
            }
        }

        List<UUID> ids = new ArrayList<>();
        for (JsonNode idNode : node.path("recommendationIds")) {
            try {
                UUID id = UUID.fromString(idNode.asText());
                ids.add(id);
            } catch (Exception ignored) {
                // ignore invalid id
            }
        }

        List<UUID> knownIds = catalog.stream().map(ListingDtos.ListingView::id).collect(Collectors.toList());
        ids = ids.stream().filter(knownIds::contains).distinct().collect(Collectors.toList());

        return new GeminiDecision(
                reply,
                new GeminiFilters(query, location, kind, maxPrice),
                ids
        );
    }

    private JsonNode parseJsonPayload(String rawText) {
        String cleaned = rawText.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("(?s)^```(?:json)?\\s*", "");
            cleaned = cleaned.replaceFirst("(?s)\\s*```\\s*$", "");
        }
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        }
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid JSON returned by Gemini: " + cleaned, ex);
        }
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private SimpleClientHttpRequestFactory buildRequestFactory() {
        int timeout = Math.max(1000, aiProperties.getGemini().getTimeoutMs());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }

    public record GeminiDecision(
            String reply,
            GeminiFilters filters,
            List<UUID> recommendationIds
    ) {
    }

    public record GeminiFilters(
            String query,
            String location,
            ListingKind kind,
            BigDecimal maxPrice
    ) {
    }
}
