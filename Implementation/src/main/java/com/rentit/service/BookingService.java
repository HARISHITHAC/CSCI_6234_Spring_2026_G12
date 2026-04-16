package com.rentit.service;

import com.rentit.config.ApiException;
import com.rentit.domain.Booking;
import com.rentit.domain.Listing;
import com.rentit.domain.UserAccount;
import com.rentit.domain.enums.BookingStatus;
import com.rentit.domain.enums.Role;
import com.rentit.dto.BookingDtos;
import com.rentit.dto.ListingDtos;
import com.rentit.repository.BookingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ListingService listingService;
    private final DtoMapper dtoMapper;

    public BookingService(BookingRepository bookingRepository, ListingService listingService, DtoMapper dtoMapper) {
        this.bookingRepository = bookingRepository;
        this.listingService = listingService;
        this.dtoMapper = dtoMapper;
    }

    @Transactional
    public BookingDtos.BookingCreateResponse createBooking(UserAccount renter, BookingDtos.CreateBookingRequest request) {
        if (request.quantity() < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Quantity must be at least 1.");
        }
        validateDates(request.startDate(), request.endDate());

        Listing listing = listingService.getListingOrThrow(request.listingId());
        if (listing.getOwner().getId().equals(renter.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot book your own listing.");
        }

        boolean available = listingService.isAvailable(listing, request.startDate(), request.endDate(), request.quantity());
        if (!available) {
            List<ListingDtos.ListingView> alternatives = listingService.findAlternatives(
                    listing,
                    request.startDate(),
                    request.endDate(),
                    request.quantity(),
                    5
            );
            return new BookingDtos.BookingCreateResponse(
                    false,
                    "Selected dates/quantity are unavailable. Here are alternatives.",
                    null,
                    alternatives
            );
        }

        Booking booking = new Booking();
        booking.setRenter(renter);
        booking.setListing(listing);
        booking.setStartDate(request.startDate());
        booking.setEndDate(request.endDate());
        booking.setQuantity(request.quantity());
        booking.setTotalPrice(calculateTotalPrice(
                listing.getPricePerDay(),
                request.startDate(),
                request.endDate(),
                request.quantity()
        ));
        booking.setStatus(listing.isHostApprovalRequired() ? BookingStatus.PENDING_APPROVAL : BookingStatus.APPROVED);

        Booking saved = bookingRepository.save(booking);
        String message = saved.getStatus() == BookingStatus.PENDING_APPROVAL
                ? "Booking request created. Waiting for host approval."
                : "Booking auto-approved. Proceed to payment.";

        return new BookingDtos.BookingCreateResponse(
                true,
                message,
                dtoMapper.toBookingView(saved),
                List.of()
        );
    }

    @Transactional(readOnly = true)
    public Booking getBookingOrThrow(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found."));
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.BookingView> myBookings(UserAccount actor) {
        return bookingRepository.findByRenterIdOrderByCreatedAtDesc(actor.getId()).stream()
                .map(dtoMapper::toBookingView)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.BookingView> hostPendingBookings(UserAccount actor) {
        if (actor.getRole() != Role.HOST && actor.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only host/admin can view pending booking requests.");
        }
        List<Booking> bookings = actor.getRole() == Role.ADMIN
                ? bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.PENDING_APPROVAL)
                : bookingRepository.findHostBookingsByStatus(actor.getId(), BookingStatus.PENDING_APPROVAL);
        return bookings.stream()
                .map(dtoMapper::toBookingView)
                .collect(Collectors.toList());
    }

    @Transactional
    public BookingDtos.BookingView decideBooking(
            UserAccount actor,
            UUID bookingId,
            BookingDtos.BookingDecisionRequest request
    ) {
        if (actor.getRole() != Role.HOST && actor.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only host/admin can approve/reject requests.");
        }
        Booking booking = getBookingOrThrow(bookingId);
        if (!booking.getListing().getOwner().getId().equals(actor.getId()) && actor.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only decide bookings for your own listings.");
        }
        if (booking.getStatus() != BookingStatus.PENDING_APPROVAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Booking is not pending approval.");
        }

        if (request.approve()) {
            booking.setStatus(BookingStatus.APPROVED);
            booking.setRejectionReason(null);
        } else {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Rejection reason is required.");
            }
            booking.setStatus(BookingStatus.REJECTED);
            booking.setRejectionReason(request.reason().trim());
        }

        Booking saved = bookingRepository.save(booking);
        return dtoMapper.toBookingView(saved);
    }

    @Transactional
    public BookingDtos.BookingView cancelBooking(UserAccount actor, UUID bookingId) {
        Booking booking = getBookingOrThrow(bookingId);

        boolean isRenter = booking.getRenter().getId().equals(actor.getId());
        boolean isHostOwner = booking.getListing().getOwner().getId().equals(actor.getId());
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        if (!isRenter && !isHostOwner && !isAdmin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot cancel this booking.");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED
                || booking.getStatus() == BookingStatus.REJECTED
                || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Booking cannot be cancelled from current state.");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Booking saved = bookingRepository.save(booking);
        return dtoMapper.toBookingView(saved);
    }

    @Transactional
    public Booking markConfirmedAfterPayment(UUID bookingId) {
        Booking booking = getBookingOrThrow(bookingId);
        if (booking.getStatus() != BookingStatus.APPROVED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only approved bookings can be confirmed by payment.");
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        return bookingRepository.save(booking);
    }

    @Transactional
    public void markCompletedIfPast(Booking booking) {
        if (booking.getStatus() == BookingStatus.CONFIRMED && booking.getEndDate().isBefore(LocalDate.now())) {
            booking.setStatus(BookingStatus.COMPLETED);
            bookingRepository.save(booking);
        }
    }

    private static void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "startDate must be before or equal to endDate.");
        }
    }

    private static BigDecimal calculateTotalPrice(
            BigDecimal pricePerDay,
            LocalDate startDate,
            LocalDate endDate,
            int quantity
    ) {
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days <= 0) {
            days = 1;
        }
        return pricePerDay
                .multiply(BigDecimal.valueOf(days))
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
