package com.rentit.service;

import com.rentit.domain.Booking;
import com.rentit.domain.ChatMessage;
import com.rentit.domain.EquipmentListing;
import com.rentit.domain.Listing;
import com.rentit.domain.Payment;
import com.rentit.domain.PropertyListing;
import com.rentit.domain.Review;
import com.rentit.domain.UserAccount;
import com.rentit.domain.enums.BookingStatus;
import com.rentit.domain.enums.ListingKind;
import com.rentit.dto.AuthDtos;
import com.rentit.dto.BookingDtos;
import com.rentit.dto.ChatDtos;
import com.rentit.dto.ListingDtos;
import com.rentit.dto.PaymentDtos;
import com.rentit.dto.ReviewDtos;
import org.springframework.stereotype.Component;

@Component
public class DtoMapper {

    public AuthDtos.UserView toUserView(UserAccount user) {
        return new AuthDtos.UserView(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    public ListingDtos.ListingView toListingView(Listing listing, boolean availableForDates) {
        ListingKind kind = listing instanceof PropertyListing ? ListingKind.PROPERTY : ListingKind.EQUIPMENT;
        String propertyType = null;
        Integer maxGuests = null;
        String equipmentType = null;
        String conditionText = null;

        if (listing instanceof PropertyListing propertyListing) {
            propertyType = propertyListing.getPropertyType();
            maxGuests = propertyListing.getMaxGuests();
        } else if (listing instanceof EquipmentListing equipmentListing) {
            equipmentType = equipmentListing.getEquipmentType();
            conditionText = equipmentListing.getConditionText();
        }

        return new ListingDtos.ListingView(
                listing.getId(),
                kind,
                listing.getTitle(),
                listing.getDescription(),
                listing.getPricePerDay(),
                listing.getLocation(),
                listing.getImageUrl(),
                listing.getStatus(),
                listing.isHostApprovalRequired(),
                listing.getTotalQuantity(),
                listing.getOwner().getId(),
                listing.getOwner().getName(),
                propertyType,
                maxGuests,
                equipmentType,
                conditionText,
                availableForDates
        );
    }

    public BookingDtos.BookingView toBookingView(Booking booking) {
        String receiptNumber = booking.getPayment() != null ? booking.getPayment().getReceiptNumber() : null;
        return new BookingDtos.BookingView(
                booking.getId(),
                booking.getListing().getId(),
                booking.getListing().getTitle(),
                booking.getRenter().getId(),
                booking.getStartDate(),
                booking.getEndDate(),
                booking.getQuantity(),
                booking.getTotalPrice(),
                booking.getStatus(),
                booking.getCreatedAt(),
                booking.getRejectionReason(),
                booking.getStatus() == BookingStatus.APPROVED,
                receiptNumber
        );
    }

    public PaymentDtos.PaymentView toPaymentView(Payment payment) {
        return new PaymentDtos.PaymentView(
                payment.getId(),
                payment.getBooking().getId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getPaidAt(),
                payment.getReceiptNumber()
        );
    }

    public ReviewDtos.ReviewView toReviewView(Review review) {
        return new ReviewDtos.ReviewView(
                review.getId(),
                review.getBooking().getId(),
                review.getListing().getId(),
                review.getAuthor().getId(),
                review.getAuthor().getName(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }

    public ChatDtos.ChatMessageView toChatMessageView(ChatMessage message) {
        return new ChatDtos.ChatMessageView(
                message.getId(),
                message.getSender(),
                message.getContent(),
                message.getTimestamp()
        );
    }
}
