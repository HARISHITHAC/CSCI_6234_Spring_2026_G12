package com.rentit.service;

import com.rentit.config.ApiException;
import com.rentit.domain.UserAccount;
import com.rentit.domain.enums.Role;
import com.rentit.repository.UserAccountRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CurrentUserService {

    private static final String SESSION_USER_ID = "AUTH_USER_ID";
    private final UserAccountRepository userAccountRepository;

    public CurrentUserService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    public void login(HttpSession session, UserAccount user) {
        session.setAttribute(SESSION_USER_ID, user.getId().toString());
    }

    public void logout(HttpSession session) {
        session.removeAttribute(SESSION_USER_ID);
    }

    public UserAccount requireUser(HttpSession session) {
        Object raw = session.getAttribute(SESSION_USER_ID);
        if (raw == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "You must login first.");
        }
        UUID userId;
        try {
            userId = UUID.fromString(raw.toString());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid session.");
        }
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found for session."));
    }

    public void requireRole(UserAccount user, Role... roles) {
        Set<Role> allowed = Arrays.stream(roles).collect(Collectors.toSet());
        if (!allowed.contains(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Insufficient permissions.");
        }
    }
}
