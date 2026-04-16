package com.rentit.service;

import com.rentit.config.ApiException;
import com.rentit.domain.Booking;
import com.rentit.domain.Review;
import com.rentit.domain.UserAccount;
import com.rentit.domain.enums.BookingStatus;
import com.rentit.domain.enums.Role;
import com.rentit.dto.ReviewDtos;
import com.rentit.repository.ReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final BookingService bookingService;
    private final ReviewRepository reviewRepository;
    private final DtoMapper dtoMapper;

    public ReviewService(BookingService bookingService, ReviewRepository reviewRepository, DtoMapper dtoMapper) {
        this.bookingService = bookingService;
        this.reviewRepository = reviewRepository;
        this.dtoMapper = dtoMapper;
    }

    @Transactional
    public ReviewDtos.ReviewView createReview(UserAccount actor, ReviewDtos.CreateReviewRequest request) {
        Booking booking = bookingService.getBookingOrThrow(request.bookingId());
        boolean isRenter = booking.getRenter().getId().equals(actor.getId());
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        if (!isRenter && !isAdmin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the renter can review this booking.");
        }

        bookingService.markCompletedIfPast(booking);
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            if (booking.getStatus() == BookingStatus.CONFIRMED && booking.getEndDate().isBefore(LocalDate.now())) {
                booking.setStatus(BookingStatus.COMPLETED);
            } else {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Review is allowed after rental is completed.");
            }
        }

        reviewRepository.findByBookingId(booking.getId()).ifPresent(existing -> {
            throw new ApiException(HttpStatus.CONFLICT, "A review already exists for this booking.");
        });

        Review review = new Review();
        review.setBooking(booking);
        review.setListing(booking.getListing());
        review.setAuthor(booking.getRenter());
        review.setRating(request.rating());
        review.setComment(request.comment().trim());

        Review saved = reviewRepository.save(review);
        booking.setReview(saved);
        return dtoMapper.toReviewView(saved);
    }

    @Transactional(readOnly = true)
    public List<ReviewDtos.ReviewView> listingReviews(UUID listingId) {
        return reviewRepository.findByListingIdOrderByCreatedAtDesc(listingId).stream()
                .map(dtoMapper::toReviewView)
                .collect(Collectors.toList());
    }
}
