package com.rentit.service;

import com.rentit.config.ApiException;
import com.rentit.domain.AvailabilitySlot;
import com.rentit.domain.Booking;
import com.rentit.domain.EquipmentListing;
import com.rentit.domain.Listing;
import com.rentit.domain.PropertyListing;
import com.rentit.domain.UserAccount;
import com.rentit.domain.enums.BookingStatus;
import com.rentit.domain.enums.ListingKind;
import com.rentit.domain.enums.ListingStatus;
import com.rentit.domain.enums.Role;
import com.rentit.dto.ListingDtos;
import com.rentit.repository.AvailabilitySlotRepository;
import com.rentit.repository.BookingRepository;
import com.rentit.repository.ListingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ListingService {

    private static final Set<BookingStatus> OCCUPYING_STATUSES = EnumSet.of(
            BookingStatus.REQUESTED,
            BookingStatus.PENDING_APPROVAL,
            BookingStatus.APPROVED,
            BookingStatus.CONFIRMED
    );
    private static final Set<String> STOP_WORDS = Set.of(
            "got", "any", "have", "has", "do", "you", "i", "we", "me", "my",
            "need", "want", "looking", "look", "show", "give", "find", "please",
            "for", "in", "on", "at", "om", "near", "the", "a", "an", "to", "and", "or",
            "with", "some", "is", "are", "can", "could", "get"
    );

    private final ListingRepository listingRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final BookingRepository bookingRepository;
    private final DtoMapper dtoMapper;

    public ListingService(
            ListingRepository listingRepository,
            AvailabilitySlotRepository availabilitySlotRepository,
            BookingRepository bookingRepository,
            DtoMapper dtoMapper
    ) {
        this.listingRepository = listingRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.bookingRepository = bookingRepository;
        this.dtoMapper = dtoMapper;
    }

    @Transactional
    public ListingDtos.ListingView createListing(UserAccount actor, ListingDtos.CreateListingRequest request) {
        if (request.totalQuantity() < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Total quantity must be at least 1.");
        }
        Listing listing;
        if (request.kind() == ListingKind.PROPERTY) {
            if (request.maxGuests() == null || request.maxGuests() < 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "maxGuests is required for property listings.");
            }
            PropertyListing propertyListing = new PropertyListing();
            propertyListing.setPropertyType(emptyToNull(request.propertyType()));
            propertyListing.setMaxGuests(request.maxGuests());
            listing = propertyListing;
        } else {
            EquipmentListing equipmentListing = new EquipmentListing();
            equipmentListing.setEquipmentType(emptyToNull(request.equipmentType()));
            equipmentListing.setConditionText(emptyToNull(request.conditionText()));
            listing = equipmentListing;
        }

        listing.setTitle(request.title().trim());
        listing.setDescription(request.description().trim());
        listing.setLocation(request.location().trim());
        listing.setImageUrl(emptyToNull(request.imageUrl()));
        listing.setPricePerDay(request.pricePerDay());
        listing.setStatus(ListingStatus.ACTIVE);
        listing.setTotalQuantity(request.totalQuantity());
        listing.setHostApprovalRequired(request.hostApprovalRequired());
        listing.setOwner(actor);

        Listing saved = listingRepository.save(listing);
        return dtoMapper.toListingView(saved, true);
    }

    @Transactional(readOnly = true)
    public Listing getListingOrThrow(UUID listingId) {
        return listingRepository.findById(listingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found."));
    }

    @Transactional(readOnly = true)
    public List<ListingDtos.ListingView> searchListings(
            String q,
            String location,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            ListingKind kind,
            LocalDate startDate,
            LocalDate endDate,
            Integer quantity
    ) {
        List<Listing> listings = listingRepository.findByStatus(ListingStatus.ACTIVE);
        String query = normalize(q);
        List<String> queryTokens = tokenize(query);
        String normalizedLocation = normalize(location);
        int qty = quantity == null || quantity < 1 ? 1 : quantity;

        return listings.stream()
                .filter(listing -> kind == null || matchesKind(listing, kind))
                .filter(listing -> query == null || matchesQuery(listing, query, queryTokens))
                .filter(listing -> normalizedLocation == null || listing.getLocation().toLowerCase(Locale.ROOT).contains(normalizedLocation))
                .filter(listing -> minPrice == null || listing.getPricePerDay().compareTo(minPrice) >= 0)
                .filter(listing -> maxPrice == null || listing.getPricePerDay().compareTo(maxPrice) <= 0)
                .map(listing -> {
                    boolean available = true;
                    if (startDate != null && endDate != null) {
                        available = isAvailable(listing, startDate, endDate, qty);
                    }
                    return dtoMapper.toListingView(listing, available);
                })
                .filter(view -> startDate == null || endDate == null || view.availableForDates())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean isAvailable(Listing listing, LocalDate startDate, LocalDate endDate, int quantity) {
        validateDateRange(startDate, endDate);
        if (quantity < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Quantity must be at least 1.");
        }
        if (listing.getStatus() != ListingStatus.ACTIVE) {
            return false;
        }
        List<AvailabilitySlot> blocked = availabilitySlotRepository.findBlockedOverlaps(listing.getId(), startDate, endDate);
        if (!blocked.isEmpty()) {
            return false;
        }
        List<Booking> bookings = bookingRepository.findOverlappingByStatuses(
                listing.getId(),
                OCCUPYING_STATUSES,
                startDate,
                endDate
        );
        int occupied = bookings.stream().mapToInt(Booking::getQuantity).sum();
        return occupied + quantity <= listing.getTotalQuantity();
    }

    @Transactional(readOnly = true)
    public List<ListingDtos.ListingView> findAlternatives(
            Listing baseListing,
            LocalDate startDate,
            LocalDate endDate,
            int quantity,
            int limit
    ) {
        List<ListingDtos.ListingView> results = new ArrayList<>();
        List<Listing> active = listingRepository.findByStatus(ListingStatus.ACTIVE);
        for (Listing candidate : active) {
            if (candidate.getId().equals(baseListing.getId())) {
                continue;
            }
            if (!candidate.getLocation().equalsIgnoreCase(baseListing.getLocation())) {
                continue;
            }
            if (matchesKind(baseListing, ListingKind.PROPERTY) && !matchesKind(candidate, ListingKind.PROPERTY)
                    || matchesKind(baseListing, ListingKind.EQUIPMENT) && !matchesKind(candidate, ListingKind.EQUIPMENT)) {
                continue;
            }
            if (isAvailable(candidate, startDate, endDate, quantity)) {
                results.add(dtoMapper.toListingView(candidate, true));
            }
            if (results.size() >= limit) {
                break;
            }
        }
        return results;
    }

    @Transactional
    public ListingDtos.AvailabilitySlotView blockDates(
            UserAccount actor,
            UUID listingId,
            ListingDtos.AvailabilityUpdateRequest request
    ) {
        validateDateRange(request.startDate(), request.endDate());
        Listing listing = getOwnedListingOrThrow(actor, listingId);
        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setListing(listing);
        slot.setStartDate(request.startDate());
        slot.setEndDate(request.endDate());
        slot.setBlocked(true);
        AvailabilitySlot saved = availabilitySlotRepository.save(slot);
        return new ListingDtos.AvailabilitySlotView(saved.getId(), saved.getStartDate(), saved.getEndDate(), saved.isBlocked());
    }

    @Transactional
    public List<ListingDtos.AvailabilitySlotView> openDates(
            UserAccount actor,
            UUID listingId,
            ListingDtos.AvailabilityUpdateRequest request
    ) {
        validateDateRange(request.startDate(), request.endDate());
        getOwnedListingOrThrow(actor, listingId);
        List<AvailabilitySlot> slots = availabilitySlotRepository.findByListingId(listingId);
        List<ListingDtos.AvailabilitySlotView> updated = new ArrayList<>();
        for (AvailabilitySlot slot : slots) {
            if (!slot.isBlocked()) {
                continue;
            }
            boolean overlaps = slot.getStartDate().compareTo(request.endDate()) <= 0
                    && slot.getEndDate().compareTo(request.startDate()) >= 0;
            if (overlaps) {
                slot.setBlocked(false);
                AvailabilitySlot saved = availabilitySlotRepository.save(slot);
                updated.add(new ListingDtos.AvailabilitySlotView(
                        saved.getId(),
                        saved.getStartDate(),
                        saved.getEndDate(),
                        saved.isBlocked()
                ));
            }
        }
        return updated;
    }

    @Transactional(readOnly = true)
    public List<ListingDtos.AvailabilitySlotView> listAvailability(UUID listingId) {
        getListingOrThrow(listingId);
        return availabilitySlotRepository.findByListingId(listingId).stream()
                .map(slot -> new ListingDtos.AvailabilitySlotView(
                        slot.getId(),
                        slot.getStartDate(),
                        slot.getEndDate(),
                        slot.isBlocked()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public ListingDtos.ListingView updateStatus(UserAccount actor, UUID listingId, ListingStatus status) {
        Listing listing = getOwnedListingOrThrow(actor, listingId);
        listing.setStatus(status);
        Listing saved = listingRepository.save(listing);
        return dtoMapper.toListingView(saved, true);
    }

    @Transactional(readOnly = true)
    public List<ListingDtos.ListingView> hostListings(UserAccount actor) {
        List<Listing> listings = listingRepository.findByOwner(actor);
        return listings.stream()
                .map(listing -> dtoMapper.toListingView(listing, true))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Set<String> activeLocations() {
        return listingRepository.findByStatus(ListingStatus.ACTIVE).stream()
                .map(Listing::getLocation)
                .filter(location -> location != null && !location.isBlank())
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<ListingDtos.ListingView> allActiveListingViews() {
        return listingRepository.findByStatus(ListingStatus.ACTIVE).stream()
                .map(listing -> dtoMapper.toListingView(listing, true))
                .collect(Collectors.toList());
    }

    private Listing getOwnedListingOrThrow(UserAccount actor, UUID listingId) {
        Listing listing = getListingOrThrow(listingId);
        boolean isOwner = listing.getOwner().getId().equals(actor.getId());
        if (!isOwner && actor.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only manage your own listing.");
        }
        return listing;
    }

    private static String normalize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return text.trim().toLowerCase(Locale.ROOT);
    }

    private static String emptyToNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private static boolean matchesKind(Listing listing, ListingKind kind) {
        return kind == ListingKind.PROPERTY ? listing instanceof PropertyListing : listing instanceof EquipmentListing;
    }

    private static boolean matchesQuery(Listing listing, String query, List<String> queryTokens) {
        String searchable = buildSearchBlob(listing);
        if (searchable.contains(query)) {
            return true;
        }

        if (queryTokens.isEmpty()) {
            return false;
        }

        Set<String> listingTokens = new HashSet<>(tokenize(searchable));
        int score = 0;
        for (String qToken : queryTokens) {
            if (qToken.length() < 2) {
                continue;
            }
            if (tokenImpliesListingKind(qToken, listing)) {
                score += 2;
                continue;
            }
            boolean exact = listingTokens.stream().anyMatch(token ->
                    token.equals(qToken) || token.startsWith(qToken) || qToken.startsWith(token)
            );
            if (exact) {
                score += 2;
                continue;
            }
            boolean fuzzy = listingTokens.stream().anyMatch(token -> isFuzzyMatch(qToken, token));
            if (fuzzy) {
                score += 1;
            }
        }
        int minScore = queryTokens.size() >= 3 ? 2 : 1;
        return score >= minScore;
    }

    private static boolean tokenImpliesListingKind(String token, Listing listing) {
        if (listing instanceof EquipmentListing) {
            return Set.of("equipment", "equip", "gear", "tool", "tools", "item", "items").contains(token);
        }
        if (listing instanceof PropertyListing) {
            return Set.of("property", "properties", "accommodation", "accommodations").contains(token);
        }
        return false;
    }

    private static String buildSearchBlob(Listing listing) {
        List<String> fields = new ArrayList<>();
        fields.add(listing.getTitle());
        fields.add(listing.getDescription());
        fields.add(listing.getLocation());

        if (listing instanceof PropertyListing propertyListing) {
            fields.add(propertyListing.getPropertyType());
        } else if (listing instanceof EquipmentListing equipmentListing) {
            fields.add(equipmentListing.getEquipmentType());
            fields.add(equipmentListing.getConditionText());
        }

        return fields.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9\\s]", " ")
                        .split("\\s+"))
                .map(ListingService::normalizeToken)
                .filter(token -> !token.isBlank())
                .filter(token -> !STOP_WORDS.contains(token))
                .distinct()
                .collect(Collectors.toList());
    }

    private static String normalizeToken(String token) {
        if (token.endsWith("ies") && token.length() > 4) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.endsWith("s") && token.length() > 3) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private static boolean isFuzzyMatch(String source, String target) {
        int maxDistance = source.length() <= 4 ? 1 : 2;
        if (Math.abs(source.length() - target.length()) > maxDistance) {
            return false;
        }
        return levenshtein(source, target, maxDistance) <= maxDistance;
    }

    private static int levenshtein(String a, String b, int maxDistance) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            int rowBest = current[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost
                );
                rowBest = Math.min(rowBest, current[j]);
            }
            if (rowBest > maxDistance) {
                return rowBest;
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }

        return previous[b.length()];
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "startDate must be before or equal to endDate.");
        }
    }
}
