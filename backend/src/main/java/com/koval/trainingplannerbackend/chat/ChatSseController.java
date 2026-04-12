package com.koval.trainingplannerbackend.chat;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatSseController {

    private final ChatSseBroadcaster broadcaster;

    public ChatSseController(ChatSseBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        // Auth is enforced by the JWT filter before this handler runs.
        String userId = SecurityUtils.getCurrentUserId();
        return broadcaster.register(userId);
    }
}
