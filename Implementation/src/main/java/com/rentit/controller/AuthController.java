package com.rentit.controller;

import com.rentit.domain.UserAccount;
import com.rentit.dto.AuthDtos;
import com.rentit.service.AuthService;
import com.rentit.service.CurrentUserService;
import com.rentit.service.DtoMapper;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;
    private final DtoMapper dtoMapper;

    public AuthController(AuthService authService, CurrentUserService currentUserService, DtoMapper dtoMapper) {
        this.authService = authService;
        this.currentUserService = currentUserService;
        this.dtoMapper = dtoMapper;
    }

    @PostMapping("/register")
    public AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        UserAccount user = authService.register(request);
        return new AuthDtos.AuthResponse(dtoMapper.toUserView(user));
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpSession session) {
        UserAccount user = authService.login(request);
        currentUserService.login(session, user);
        return new AuthDtos.AuthResponse(dtoMapper.toUserView(user));
    }

    @PostMapping("/logout")
    public void logout(HttpSession session) {
        currentUserService.logout(session);
    }

    @GetMapping("/me")
    public AuthDtos.AuthResponse me(HttpSession session) {
        UserAccount user = currentUserService.requireUser(session);
        return new AuthDtos.AuthResponse(dtoMapper.toUserView(user));
    }
}
