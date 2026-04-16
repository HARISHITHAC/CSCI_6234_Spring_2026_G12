package com.rentit.controller;

import com.rentit.domain.UserAccount;
import com.rentit.dto.ChatDtos;
import com.rentit.service.ChatbotService;
import com.rentit.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatbotService chatbotService;
    private final CurrentUserService currentUserService;

    public ChatController(ChatbotService chatbotService, CurrentUserService currentUserService) {
        this.chatbotService = chatbotService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/start")
    public ChatDtos.StartSessionResponse start(HttpSession session) {
        UserAccount actor = currentUserService.requireUser(session);
        return chatbotService.startSession(actor);
    }

    @PostMapping("/message")
    public ChatDtos.ChatReplyResponse message(
            @Valid @RequestBody ChatDtos.SendMessageRequest request,
            HttpSession session
    ) {
        UserAccount actor = currentUserService.requireUser(session);
        return chatbotService.sendMessage(actor, request);
    }
}
