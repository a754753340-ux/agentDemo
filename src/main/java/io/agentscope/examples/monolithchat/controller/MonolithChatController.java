//package io.agentscope.examples.monolithchat.controller;
//
//import io.agentscope.core.agent.Event;
//import io.agentscope.core.message.ContentBlock;
//import io.agentscope.core.message.Msg;
//import io.agentscope.core.message.MsgRole;
//import io.agentscope.core.message.TextBlock;
//import io.agentscope.core.message.ToolResultBlock;
//import io.agentscope.examples.monolithchat.dto.UserCard;
//import io.agentscope.examples.monolithchat.dto.UserCardsAdd;
//import io.agentscope.examples.monolithchat.agentscope.SupervisorAgent;
//import io.agentscope.examples.monolithchat.dto.ChatRequest;
//import io.agentscope.examples.monolithchat.dto.ErrorEvent;
//import io.agentscope.examples.monolithchat.dto.ImageItem;
//import io.agentscope.examples.monolithchat.dto.ImagesAdd;
//import io.agentscope.examples.monolithchat.dto.MessageComplete;
//import io.agentscope.examples.monolithchat.dto.MessageStart;
//import io.agentscope.examples.monolithchat.dto.StreamEvent;
//import io.agentscope.examples.monolithchat.dto.TextDelta;
//import io.agentscope.examples.monolithchat.service.MonolithChatService;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
//import org.springframework.http.codec.ServerSentEvent;
//import org.springframework.web.bind.annotation.*;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Sinks;
//
//import java.time.Instant;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//import java.util.UUID;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.atomic.AtomicLong;
//import java.util.concurrent.atomic.AtomicReference;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//@RestController
//@Slf4j
//@RequestMapping("/api")
//public class MonolithChatController {
//
//    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
//    private static final Pattern MARKDOWN_IMAGE_PATTERN =
//            Pattern.compile("!\\[[^\\]]*\\]\\((?<url>[^)\\s]+)\\)");
//    private static final Pattern IMAGE_URL_PATTERN =
//            Pattern.compile("(?i)\\bhttps?://[^\\s)\\]}>]+\\.(png|jpe?g|gif|webp)(\\?[^\\s)\\]}>]+)?");
//
//    private final SupervisorAgent supervisorAgent;
//
//    private final MonolithChatService monolithChatService;
//
//    public MonolithChatController(MonolithChatService monolithChatService, SupervisorAgent supervisorAgent) {
//        this.monolithChatService = monolithChatService;
//        this.supervisorAgent = supervisorAgent;
//    }
//
//    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<ServerSentEvent<StreamEvent<?>>> chat(@RequestBody ChatRequest request) {
////        String sessionId = request.getSessionId();
////        if (sessionId == null || sessionId.isBlank()) {
////            sessionId = "default";
////        }
////        return monolithChatService
////                .chat(sessionId, request.getMessage())
////                .map(event -> ServerSentEvent.<ChatEvent>builder().data(event).build());
//        try {
//            String sessionId = request.getSessionId();
//            if (sessionId == null || sessionId.isBlank()) {
//                sessionId = UUID.randomUUID().toString();
//            }
//            String userId = request.getUserId();
//            if (userId == null || userId.isBlank()) {
//                userId = "55134";
//            }
//
////            String userInput = request.getMessage() + "<userId>" + request.getSessionId() + "</userId>";
//            Sinks.Many<ServerSentEvent<StreamEvent<?>>> sink =
//                    Sinks.many().unicast().onBackpressureBuffer();
//            Msg msg =
//                    Msg.builder()
//                            .role(MsgRole.USER)
//                            .content(TextBlock.builder().text(request.getMessage()).build())
//                            .build();
//            String messageId = UUID.randomUUID().toString();
//            processStream(supervisorAgent.stream(msg, sessionId, userId), sink, sessionId, userId, messageId);
//
//            return sink.asFlux()
//                    .doOnCancel(
//                            () -> {
//                                log.info("Client disconnected from stream");
//                            })
//                    .doOnError(
//                            e -> {
//                                log.error("Error occurred during streaming", e);
//                            });
//        } catch (Exception e) {
//            log.error("Failed to process user query: {}", e);
//            StreamEvent<MessageComplete> event = new StreamEvent<>();
//            event.setId("error");
//            event.setType("error");
//            event.setDelta(false);
//            event.setSessionId(request.getSessionId());
//            event.setUserId(request.getUserId());
//            event.setTs(Instant.now().toEpochMilli());
//            MessageComplete data = new MessageComplete();
//            data.setMessageId(null);
//            data.setFinishReason("error");
//            event.setData(data);
//            return Flux.just(ServerSentEvent.<StreamEvent<?>>builder(event).event(event.getType()).id(event.getId()).build());
//        }
//    }
//
//    public void processStream(
//            Flux<Event> generator,
//            Sinks.Many<ServerSentEvent<StreamEvent<?>>> sink,
//            String sessionId,
//            String userId,
//            String messageId) {
//        AtomicLong seq = new AtomicLong(1);
//        AtomicReference<String> lastFullTextRef = new AtomicReference<>();
//        Set<String> sentImages = ConcurrentHashMap.newKeySet();
//        StreamEvent<MessageStart> start = new StreamEvent<>();
//        start.setId(String.valueOf(seq.getAndIncrement()));
//        start.setType("message.start");
//        start.setDelta(false);
//        start.setSessionId(sessionId);
//        start.setUserId(userId);
//        start.setTs(Instant.now().toEpochMilli());
//        MessageStart startData = new MessageStart();
//        startData.setMessageId(messageId);
//        startData.setRole("assistant");
//        start.setData(startData);
//        sink.tryEmitNext(
//                ServerSentEvent.<StreamEvent<?>>builder(start)
//                        .event(start.getType())
//                        .id(start.getId())
//                        .build());
//        generator
//                .doOnNext(output -> log.info("output = {}", output))
//                .flatMap(
//                        event -> Flux.fromIterable(
//                                mapToStreamEvents(event, sessionId, userId, messageId, seq, lastFullTextRef, sentImages)))
//                .map(this::toSse)
//                .doOnNext(sink::tryEmitNext)
//                .doOnError(
//                        e -> {
//                            log.error(
//                                    "Unexpected error in stream processing: {}", e.getMessage(), e);
//                            sink.tryEmitNext(toSse(buildErrorEvent(sessionId, userId, messageId, seq, e)));
//                            sink.tryEmitNext(toSse(buildCompleteEvent(sessionId, userId, messageId, seq, "error")));
//                            sink.tryEmitComplete();
//                        })
//                .doOnComplete(
//                        () -> {
//                            sink.tryEmitNext(toSse(buildCompleteEvent(sessionId, userId, messageId, seq, "stop")));
//                            sink.tryEmitComplete();
//                        })
//                .subscribe();
//    }
//
//    private ServerSentEvent<StreamEvent<?>> toSse(StreamEvent<?> event) {
//        return ServerSentEvent.<StreamEvent<?>>builder(event)
//                .event(event.getType())
//                .id(event.getId())
//                .build();
//    }
//
//    private List<StreamEvent<?>> mapToStreamEvents(
//            Event inputEvent,
//            String sessionId,
//            String userId,
//            String messageId,
//            AtomicLong seq,
//            AtomicReference<String> lastFullTextRef,
//            Set<String> sentImages) {
//        List<StreamEvent<?>> out = new ArrayList<>();
//        List<UserCard> cards = extractUserCards(inputEvent.getMessage());
//        if (!cards.isEmpty()) {
//            out.add(buildUserCardsAddEvent(sessionId, userId, messageId, seq, cards));
//            List<ImageItem> cardImages = new ArrayList<>();
//            for (UserCard card : cards) {
//                if (card.getAvatar() != null && !card.getAvatar().isBlank() && sentImages.add(card.getAvatar().strip())) {
//                    ImageItem item = new ImageItem();
//                    item.setId(Integer.toHexString(card.getAvatar().hashCode()));
//                    item.setUrl(card.getAvatar().strip());
//                    item.setAlt(card.getNickname());
//                    cardImages.add(item);
//                }
//            }
//            if (!cardImages.isEmpty()) {
//                out.add(buildImagesAddEvent(sessionId, userId, messageId, seq, cardImages));
//            }
//        }
//        String chunkText = extractText(inputEvent.getMessage());
//        if (chunkText == null || chunkText.isBlank()) {
//            return out;
//        }
//        String lastFullText = lastFullTextRef.get();
//        String newFullText;
//        String delta;
//        if (lastFullText != null && chunkText.startsWith(lastFullText)) {
//            delta = chunkText.substring(lastFullText.length());
//            newFullText = chunkText;
//        } else {
//            delta = chunkText;
//            newFullText = (lastFullText == null ? "" : lastFullText) + delta;
//        }
//        if (delta.isBlank()) {
//            lastFullTextRef.set(newFullText);
//            return out;
//        }
//        lastFullTextRef.set(newFullText);
//        out.add(buildTextDeltaEvent(sessionId, userId, messageId, seq, delta));
//        List<ImageItem> newImages = extractNewImages(newFullText, sentImages);
//        if (!newImages.isEmpty()) {
//            out.add(buildImagesAddEvent(sessionId, userId, messageId, seq, newImages));
//        }
//        return out;
//    }
//
//    private String extractText(Msg msg) {
//        if (msg == null || msg.getContent() == null) {
//            return "";
//        }
//        return msg.getContent().stream()
//                .filter(block -> block instanceof TextBlock)
//                .map(block -> ((TextBlock) block).getText())
//                .reduce("", (a, b) -> a + (b == null ? "" : b));
//    }
//
//    private List<UserCard> extractUserCards(Msg msg) {
//        if (msg == null || msg.getContent() == null || !msg.hasContentBlocks(ToolResultBlock.class)) {
//            return List.of();
//        }
//        List<UserCard> cards = new ArrayList<>();
//        for (ToolResultBlock result : msg.getContentBlocks(ToolResultBlock.class)) {
//            List<ContentBlock> outputs = result.getOutput();
//            if (outputs == null || outputs.isEmpty()) {
//                continue;
//            }
//            for (ContentBlock output : outputs) {
//                if (!(output instanceof TextBlock tb)) {
//                    continue;
//                }
//                cards.addAll(parseUserCards(tb.getText()));
//            }
//        }
//        return cards;
//    }
//
//    private List<UserCard> parseUserCards(String text) {
//        if (text == null || text.isBlank()) {
//            return List.of();
//        }
//        try {
//            JsonNode root = OBJECT_MAPPER.readTree(text);
//            String type = root.path("type").asText();
//            if (!"user_list".equals(type) || !root.path("data").isArray()) {
//                return List.of();
//            }
//            List<UserCard> cards = new ArrayList<>();
//            for (JsonNode node : root.path("data")) {
//                UserCard card = new UserCard();
//                card.setUserId(node.path("userId").asText(node.path("id").asText()));
//                card.setNickname(node.path("nickname").asText(null));
//                card.setAvatar(node.path("avatar").asText(null));
//                if (!node.path("gender").isMissingNode() && !node.path("gender").isNull()) {
//                    card.setGender(node.path("gender").asInt());
//                }
//                if (!node.path("diamond").isMissingNode() && !node.path("diamond").isNull()) {
//                    card.setDiamond(node.path("diamond").asInt());
//                }
//                cards.add(card);
//            }
//            return cards;
//        } catch (Exception ignored) {
//            return List.of();
//        }
//    }
//
//    private List<ImageItem> extractNewImages(String text, Set<String> sentImages) {
//        if (text == null || text.isBlank()) {
//            return List.of();
//        }
//        List<String> urls = new ArrayList<>();
//        Matcher mdMatcher = MARKDOWN_IMAGE_PATTERN.matcher(text);
//        while (mdMatcher.find()) {
//            String url = mdMatcher.group("url");
//            if (url != null && !url.isBlank()) {
//                urls.add(url.strip());
//            }
//        }
//        Matcher urlMatcher = IMAGE_URL_PATTERN.matcher(text);
//        while (urlMatcher.find()) {
//            String url = urlMatcher.group();
//            if (url != null && !url.isBlank()) {
//                urls.add(url.strip());
//            }
//        }
//        List<ImageItem> newItems = new ArrayList<>();
//        for (String url : urls) {
//            if (sentImages.add(url)) {
//                ImageItem item = new ImageItem();
//                item.setId(Integer.toHexString(url.hashCode()));
//                item.setUrl(url);
//                newItems.add(item);
//            }
//        }
//        return newItems;
//    }
//
//    private StreamEvent<TextDelta> buildTextDeltaEvent(
//            String sessionId, String userId, String messageId, AtomicLong seq, String deltaText) {
//        StreamEvent<TextDelta> event = new StreamEvent<>();
//        event.setId(String.valueOf(seq.getAndIncrement()));
//        event.setType("message.text.delta");
//        event.setDelta(true);
//        event.setSessionId(sessionId);
//        event.setUserId(userId);
//        event.setTs(Instant.now().toEpochMilli());
//        TextDelta data = new TextDelta();
//        data.setMessageId(messageId);
//        data.setText(deltaText);
//        data.setFormat("plain");
//        event.setData(data);
//        return event;
//    }
//
//    private StreamEvent<ImagesAdd> buildImagesAddEvent(
//            String sessionId, String userId, String messageId, AtomicLong seq, List<ImageItem> images) {
//        StreamEvent<ImagesAdd> event = new StreamEvent<>();
//        event.setId(String.valueOf(seq.getAndIncrement()));
//        event.setType("message.images.add");
//        event.setDelta(true);
//        event.setSessionId(sessionId);
//        event.setUserId(userId);
//        event.setTs(Instant.now().toEpochMilli());
//        ImagesAdd data = new ImagesAdd();
//        data.setMessageId(messageId);
//        data.setImages(images);
//        event.setData(data);
//        return event;
//    }
//
//    private StreamEvent<UserCardsAdd> buildUserCardsAddEvent(
//            String sessionId, String userId, String messageId, AtomicLong seq, List<UserCard> cards) {
//        StreamEvent<UserCardsAdd> event = new StreamEvent<>();
//        event.setId(String.valueOf(seq.getAndIncrement()));
//        event.setType("message.user.cards.add");
//        event.setDelta(true);
//        event.setSessionId(sessionId);
//        event.setUserId(userId);
//        event.setTs(Instant.now().toEpochMilli());
//        UserCardsAdd data = new UserCardsAdd();
//        data.setMessageId(messageId);
//        data.setUsers(cards);
//        event.setData(data);
//        return event;
//    }
//
//    private StreamEvent<MessageComplete> buildCompleteEvent(
//            String sessionId, String userId, String messageId, AtomicLong seq, String finishReason) {
//        StreamEvent<MessageComplete> event = new StreamEvent<>();
//        event.setId(String.valueOf(seq.getAndIncrement()));
//        event.setType("message.complete");
//        event.setDelta(false);
//        event.setSessionId(sessionId);
//        event.setUserId(userId);
//        event.setTs(Instant.now().toEpochMilli());
//        MessageComplete data = new MessageComplete();
//        data.setMessageId(messageId);
//        data.setFinishReason(finishReason);
//        event.setData(data);
//        return event;
//    }
//
//    private StreamEvent<ErrorEvent> buildErrorEvent(
//            String sessionId, String userId, String messageId, AtomicLong seq, Throwable error) {
//        StreamEvent<ErrorEvent> event = new StreamEvent<>();
//        event.setId(String.valueOf(seq.getAndIncrement()));
//        event.setType("error");
//        event.setDelta(false);
//        event.setSessionId(sessionId);
//        event.setUserId(userId);
//        event.setTs(Instant.now().toEpochMilli());
//        ErrorEvent data = new ErrorEvent();
//        data.setMessageId(messageId);
//        data.setCode("STREAM_ERROR");
//        data.setMessage(error == null ? "unknown error" : String.valueOf(error.getMessage()));
//        event.setData(data);
//        return event;
//    }
//
////    @DeleteMapping("/chat/session/{sessionId}")
////    public ResponseEntity<Map<String, Object>> clearSession(@PathVariable String sessionId) {
////        monolithChatService.clearSession(sessionId);
////        return ResponseEntity.ok(Map.of("success", true));
////    }
////
////    @PostMapping("/chat/interrupt/{sessionId}")
////    public ResponseEntity<Map<String, Object>> interrupt(@PathVariable String sessionId) {
////        boolean interrupted = monolithChatService.interrupt(sessionId);
////        return ResponseEntity.ok(Map.of("success", true, "interrupted", interrupted));
////    }
//
//    @GetMapping("/tools")
//    public ResponseEntity<Set<String>> getTools() {
//        return ResponseEntity.ok(monolithChatService.getToolNames());
//    }
//
//    @GetMapping("/health")
//    public ResponseEntity<Map<String, Object>> health() {
//        return ResponseEntity.ok(Map.of("status", "UP", "service", "monolith-chat"));
//    }
//}
