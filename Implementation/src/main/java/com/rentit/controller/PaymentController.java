package com.rentit.controller;

import com.rentit.domain.UserAccount;
import com.rentit.dto.PaymentDtos;
import com.rentit.service.CurrentUserService;
import com.rentit.service.PaymentService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUserService currentUserService;

    public PaymentController(PaymentService paymentService, CurrentUserService currentUserService) {
        this.paymentService = paymentService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/pay")
    public PaymentDtos.PaymentView pay(
            @Valid @RequestBody PaymentDtos.PayRequest request,
            HttpSession session
    ) {
        UserAccount actor = currentUserService.requireUser(session);
        return paymentService.pay(actor, request);
    }
}
