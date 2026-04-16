package com.rentit.controller;

import com.rentit.config.ApiException;
import com.rentit.domain.Booking;
import com.rentit.domain.UserAccount;
import com.rentit.domain.enums.Role;
import com.rentit.dto.BookingDtos;
import com.rentit.service.BookingService;
import com.rentit.service.CurrentUserService;
import com.rentit.service.DtoMapper;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final CurrentUserService currentUserService;
    private final DtoMapper dtoMapper;

    public BookingController(BookingService bookingService, CurrentUserService currentUserService, DtoMapper dtoMapper) {
        this.bookingService = bookingService;
        this.currentUserService = currentUserService;
        this.dtoMapper = dtoMapper;
    }

    @PostMapping
    public BookingDtos.BookingCreateResponse create(
            @Valid @RequestBody BookingDtos.CreateBookingRequest request,
            HttpSession session
    ) {
        UserAccount actor = currentUserService.requireUser(session);
        currentUserService.requireRole(actor, Role.RENTER, Role.HOST, Role.ADMIN);
        return bookingService.createBooking(actor, request);
    }

    @GetMapping("/me")
    public List<BookingDtos.BookingView> myBookings(HttpSession session) {
        UserAccount actor = currentUserService.requireUser(session);
        return bookingService.myBookings(actor);
    }

    @GetMapping("/host/pending")
    public List<BookingDtos.BookingView> hostPending(HttpSession session) {
        UserAccount actor = currentUserService.requireUser(session);
        return bookingService.hostPendingBookings(actor);
    }

    @GetMapping("/{id}")
    public BookingDtos.BookingView bookingDetails(@PathVariable UUID id, HttpSession session) {
        UserAccount actor = currentUserService.requireUser(session);
        Booking booking = bookingService.getBookingOrThrow(id);

        boolean isRenter = booking.getRenter().getId().equals(actor.getId());
        boolean isHostOwner = booking.getListing().getOwner().getId().equals(actor.getId());
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        if (!isRenter && !isHostOwner && !isAdmin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot access this booking.");
        }
        return dtoMapper.toBookingView(booking);
    }

    @PostMapping("/{id}/decision")
    public BookingDtos.BookingView decide(
            @PathVariable UUID id,
            @Valid @RequestBody BookingDtos.BookingDecisionRequest request,
            HttpSession session
    ) {
        UserAccount actor = currentUserService.requireUser(session);
        return bookingService.decideBooking(actor, id, request);
    }

    @PostMapping("/{id}/cancel")
    public BookingDtos.BookingView cancel(@PathVariable UUID id, HttpSession session) {
        UserAccount actor = currentUserService.requireUser(session);
        return bookingService.cancelBooking(actor, id);
    }
}
