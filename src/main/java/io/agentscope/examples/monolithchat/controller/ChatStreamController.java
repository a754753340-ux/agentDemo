package io.agentscope.examples.monolithchat.controller;

import io.agentscope.examples.monolithchat.dto.ChatSendRequest;
import io.agentscope.examples.monolithchat.dto.ChatSendResponse;
import io.agentscope.examples.monolithchat.dto.TagSelectRequest;
import io.agentscope.examples.monolithchat.dto.TagSelectResponse;
import io.agentscope.examples.monolithchat.dto.ToolConfirmRequest;
import io.agentscope.examples.monolithchat.dto.ToolConfirmResponse;
import io.agentscope.examples.monolithchat.service.ChatStreamService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatStreamController {

    private final ChatStreamService chatStreamService;

    public ChatStreamController(ChatStreamService chatStreamService) {
        this.chatStreamService = chatStreamService;
    }

    @PostMapping("/send")
    public ChatSendResponse send(@RequestBody ChatSendRequest request) {
        if (request == null || request.getSessionId() == null || request.getSessionId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId is required");
        }
        return chatStreamService.send(request);
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(
            @RequestParam("sessionId") String sessionId,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "triggerMessageId", required = false) String triggerMessageId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId is required");
        }
        String requestUserId = userId;
        if (requestUserId == null || requestUserId.isBlank()) {
            requestUserId = "55134";
        }
        return chatStreamService.stream(sessionId, requestUserId, triggerMessageId);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> streamAlias(
            @RequestParam("sessionId") String sessionId,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "triggerMessageId", required = false) String triggerMessageId) {
        return stream(sessionId, userId, triggerMessageId);
    }

    @PostMapping("/confirm")
    public ToolConfirmResponse confirm(@RequestBody ToolConfirmRequest request) {
        if (request == null || request.getSessionId() == null || request.getSessionId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId is required");
        }
        if (request.getConfirmationId() == null || request.getConfirmationId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "confirmationId is required");
        }
        return chatStreamService.confirmTool(request);
    }

    @PostMapping("/tag/select")
    public TagSelectResponse selectTag(@RequestBody TagSelectRequest request) {
        if (request == null || request.getSessionId() == null || request.getSessionId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId is required");
        }
        if (request.getTaskId() == null || request.getTaskId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskId is required");
        }
        if (request.getTagId() == null || request.getTagId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tagId is required");
        }
        return chatStreamService.selectTag(request);
    }
}
