package com.rentit.service;

import com.rentit.config.ApiException;
import com.rentit.domain.Booking;
import com.rentit.domain.Payment;
import com.rentit.domain.UserAccount;
import com.rentit.domain.enums.BookingStatus;
import com.rentit.domain.enums.PaymentStatus;
import com.rentit.domain.enums.Role;
import com.rentit.dto.PaymentDtos;
import com.rentit.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class PaymentService {

    private final BookingService bookingService;
    private final PaymentRepository paymentRepository;
    private final DtoMapper dtoMapper;

    public PaymentService(BookingService bookingService, PaymentRepository paymentRepository, DtoMapper dtoMapper) {
        this.bookingService = bookingService;
        this.paymentRepository = paymentRepository;
        this.dtoMapper = dtoMapper;
    }

    @Transactional
    public PaymentDtos.PaymentView pay(UserAccount actor, PaymentDtos.PayRequest request) {
        Booking booking = bookingService.getBookingOrThrow(request.bookingId());
        boolean isRenter = booking.getRenter().getId().equals(actor.getId());
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        if (!isRenter && !isAdmin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only pay for your own booking.");
        }
        if (booking.getStatus() != BookingStatus.APPROVED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Booking is not ready for payment.");
        }
        Payment payment = paymentRepository.findByBookingId(booking.getId()).orElse(null);
        if (payment != null && payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Booking is already paid.");
        }
        if (payment == null) {
            payment = new Payment();
            payment.setBooking(booking);
        }

        payment.setAmount(booking.getTotalPrice());
        payment.setMethod(request.method().trim());
        payment.setPaidAt(Instant.now());
        payment.setStatus(PaymentStatus.INITIATED);

        String normalizedMethod = request.method().trim().toUpperCase(Locale.ROOT);
        if ("FAIL".equals(normalizedMethod)) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setReceiptNumber(null);
            Payment savedFailed = paymentRepository.save(payment);
            return dtoMapper.toPaymentView(savedFailed);
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setReceiptNumber(generateReceipt());
        Payment saved = paymentRepository.save(payment);

        booking.setPayment(saved);
        bookingService.markConfirmedAfterPayment(booking.getId());
        return dtoMapper.toPaymentView(saved);
    }

    private static String generateReceipt() {
        return "RCPT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
