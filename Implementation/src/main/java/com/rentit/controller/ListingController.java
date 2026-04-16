package com.rentit.controller;

import com.rentit.domain.Listing;
import com.rentit.domain.UserAccount;
import com.rentit.domain.enums.ListingKind;
import com.rentit.domain.enums.ListingStatus;
import com.rentit.domain.enums.Role;
import com.rentit.dto.ListingDtos;
import com.rentit.service.CurrentUserService;
import com.rentit.service.DtoMapper;
import com.rentit.service.ListingService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingService listingService;
    private final CurrentUserService currentUserService;
    private final DtoMapper dtoMapper;

    public ListingController(ListingService listingService, CurrentUserService currentUserService, DtoMapper dtoMapper) {
        this.listingService = listingService;
        this.currentUserService = currentUserService;
        this.dtoMapper = dtoMapper;
    }

    @GetMapping
    public ListingDtos.SearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) ListingKind kind,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer quantity
    ) {
        List<ListingDtos.ListingView> listings = listingService.searchListings(
                q, location, minPrice, maxPrice, kind, startDate, endDate, quantity
        );
        return new ListingDtos.SearchResponse(listings);
    }

    @GetMapping("/{id}")
    public ListingDtos.ListingView details(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer quantity
    ) {
        Listing listing = listingService.getListingOrThrow(id);
        boolean available = true;
        if (startDate != null && endDate != null) {
            available = listingService.isAvailable(listing, startDate, endDate, quantity == null ? 1 : quantity);
        }
        return dtoMapper.toListingView(listing, available);
    }

    @GetMapping("/host/mine")
    public List<ListingDtos.ListingView> myListings(HttpSession session) {
        UserAccount actor = currentUserService.requireUser(session);
        currentUserService.requireRole(actor, Role.HOST, Role.ADMIN);
        return listingService.hostListings(actor);
    }

    @PostMapping
    public ListingDtos.ListingView create(
            @Valid @RequestBody ListingDtos.CreateListingRequest request,
            HttpSession session
    ) {
        UserAccount actor = currentUserService.requireUser(session);
        currentUserService.requireRole(actor, Role.HOST, Role.ADMIN);
        return listingService.createListing(actor, request);
    }

    @PatchMapping("/{id}/status")
    public ListingDtos.ListingView updateStatus(
            @PathVariable UUID id,
            @RequestParam ListingStatus status,
            HttpSession session
    ) {
        UserAccount actor = currentUserService.requireUser(session);
        currentUserService.requireRole(actor, Role.HOST, Role.ADMIN);
        return listingService.updateStatus(actor, id, status);
    }

    @GetMapping("/{id}/availability")
    public List<ListingDtos.AvailabilitySlotView> availability(@PathVariable UUID id) {
        return listingService.listAvailability(id);
    }

    @PostMapping("/{id}/availability/block")
    public ListingDtos.AvailabilitySlotView blockDates(
            @PathVariable UUID id,
            @Valid @RequestBody ListingDtos.AvailabilityUpdateRequest request,
            HttpSession session
    ) {
        UserAccount actor = currentUserService.requireUser(session);
        currentUserService.requireRole(actor, Role.HOST, Role.ADMIN);
        return listingService.blockDates(actor, id, request);
    }

    @PostMapping("/{id}/availability/open")
    public List<ListingDtos.AvailabilitySlotView> openDates(
            @PathVariable UUID id,
            @Valid @RequestBody ListingDtos.AvailabilityUpdateRequest request,
            HttpSession session
    ) {
        UserAccount actor = currentUserService.requireUser(session);
        currentUserService.requireRole(actor, Role.HOST, Role.ADMIN);
        return listingService.openDates(actor, id, request);
    }
}
