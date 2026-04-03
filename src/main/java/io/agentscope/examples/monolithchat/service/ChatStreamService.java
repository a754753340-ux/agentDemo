package io.agentscope.examples.monolithchat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.examples.monolithchat.agentscope.SupervisorAgent;
import io.agentscope.examples.monolithchat.dto.ChatSendContentBlock;
import io.agentscope.examples.monolithchat.dto.ChatSendRequest;
import io.agentscope.examples.monolithchat.dto.ChatSendResponse;
import io.agentscope.examples.monolithchat.dto.ImageUrlItem;
import io.agentscope.examples.monolithchat.dto.StreamBlockData;
import io.agentscope.examples.monolithchat.dto.StreamDeltaData;
import io.agentscope.examples.monolithchat.dto.StreamEndData;
import io.agentscope.examples.monolithchat.dto.StreamStartData;
import io.agentscope.examples.monolithchat.dto.StreamToolConfirmData;
import io.agentscope.examples.monolithchat.dto.ToolConfirmRequest;
import io.agentscope.examples.monolithchat.dto.ToolConfirmResponse;
import io.agentscope.examples.monolithchat.entity.ChatMessage;
import io.agentscope.examples.monolithchat.entity.MessageContent;
import io.agentscope.examples.monolithchat.mapper.ChatMessageMapper;
import io.agentscope.examples.monolithchat.mapper.MessageContentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.core.Disposable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ChatStreamService {

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String STATUS_STREAMING = "streaming";
    private static final String STATUS_DONE = "done";
    private static final String TYPE_TEXT = "text";
    private static final String TYPE_IMAGE_LIST = "image_list";
    private static final String TYPE_TOOL_CONFIRM = "tool_confirm";
    private static final String DEFAULT_USER_ID = "55134";
    private static final List<String> SENSITIVE_TOOLS = List.of("delete_file", "send_email");
    private static final Pattern MARKDOWN_IMAGE_PATTERN =
            Pattern.compile("!\\[[^\\]]*\\]\\((?<url>[^)\\s]+)\\)");
    private static final Pattern IMAGE_URL_PATTERN =
            Pattern.compile("(?i)\\bhttps?://[^\\s)\\]}>]+\\.(png|jpe?g|gif|webp)(\\?[^\\s)\\]}>]+)?");

    private final ChatMessageMapper chatMessageMapper;
    private final MessageContentMapper messageContentMapper;
    private final ObjectMapper objectMapper;
    private final SupervisorAgent supervisorAgent;
    private final ToolConfirmationService toolConfirmationService;

    public ChatStreamService(
            ChatMessageMapper chatMessageMapper,
            MessageContentMapper messageContentMapper,
            ObjectMapper objectMapper,
            SupervisorAgent supervisorAgent,
            ToolConfirmationService toolConfirmationService) {
        this.chatMessageMapper = chatMessageMapper;
        this.messageContentMapper = messageContentMapper;
        this.objectMapper = objectMapper;
        this.supervisorAgent = supervisorAgent;
        this.toolConfirmationService = toolConfirmationService;
    }

    public ChatSendResponse send(ChatSendRequest request) {
        String sessionId = normalizeSessionId(request.getSessionId());
        String messageId = UUID.randomUUID().toString();
        int seq = nextSeq(sessionId);
        ChatMessage message = new ChatMessage();
        message.setMessageId(messageId);
        message.setSessionId(sessionId);
        message.setUserId(request.getUserId());
        message.setRole(ROLE_USER);
        message.setStatus(STATUS_DONE);
        message.setSeq(seq);
        message.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(message);
        persistContentBlocks(messageId, normalizeBlocks(request.getContent()));
        ChatSendResponse response = new ChatSendResponse();
        response.setMessageId(messageId);
        response.setSessionId(sessionId);
        response.setStatus(STATUS_DONE);
        return response;
    }

    public Flux<ServerSentEvent<Object>> stream(String sessionId, String userId, String triggerMessageId) {
        return Flux.<ServerSentEvent<Object>>create(sink -> {
                    // 1) 归一化请求参数并创建 assistant 占位消息（streaming）
                    String safeSessionId = normalizeSessionId(sessionId);
                    String safeUserId = normalizeUserId(userId);
                    String messageId = UUID.randomUUID().toString();

                    // 2) 推送 start 事件，通知前端创建本轮 assistant 消息容器
                    StreamStartData startData = new StreamStartData();
                    startData.setMessageId(messageId);
                    sink.next(SseEventUtil.build("start", startData));

                    ChatMessage assistant;
                    try {
                        int seq = nextSeq(safeSessionId);
                        assistant = new ChatMessage();
                        assistant.setMessageId(messageId);
                        assistant.setSessionId(safeSessionId);
                        assistant.setUserId(safeUserId);
                        assistant.setRole(ROLE_ASSISTANT);
                        assistant.setStatus(STATUS_STREAMING);
                        assistant.setSeq(seq);
                        assistant.setCreateTime(LocalDateTime.now());
                        chatMessageMapper.insert(assistant);
                    } catch (Throwable e) {
                        StreamDeltaData deltaData = new StreamDeltaData();
                        deltaData.setType(TYPE_TEXT);
                        deltaData.setDelta("服务端初始化失败");
                        sink.next(SseEventUtil.build("delta", deltaData));
                        StreamEndData endData = new StreamEndData();
                        endData.setMessageId(messageId);
                        sink.next(SseEventUtil.build("end", endData));
                        sink.complete();
                        return;
                    }

                    // 3) 读取触发消息的 content block，组装成模型输入
                    List<ChatSendContentBlock> inputBlocks = findUserBlocks(safeSessionId, safeUserId, triggerMessageId);
                    Msg msg = Msg.builder()
                            .role(MsgRole.USER)
                            .content(TextBlock.builder().text(toPromptText(inputBlocks)).build())
                            .build();

                    // 4) 流式状态缓存：
                    //    fullTextRef: 对前端可见文本（会落库）
                    //    rawTextRef:  模型原始累计文本（用于增量计算）
                    String[] fullTextRef = {""};
                    String[] rawTextRef = {""};
                    Set<String> imageUrls = new LinkedHashSet<>();
                    Set<String> emittedImageUrls = new LinkedHashSet<>();
                    JsonLeakFilterState jsonLeakFilterState = new JsonLeakFilterState();
                    AtomicBoolean finalized = new AtomicBoolean(false);
                    AtomicReference<Throwable> streamError = new AtomicReference<>();
                    AtomicBoolean interruptedForConfirm = new AtomicBoolean(false);
                    AtomicReference<Disposable> disposableRef = new AtomicReference<>();
                    Runnable finalizeStream = () -> {
                        if (!finalized.compareAndSet(false, true)) {
                            return;
                        }
                        List<ImageUrlItem> images = toImageItems(imageUrls);
                        Throwable error = streamError.get();
                        try {
                            if (error != null) {
                                log.error("Unexpected error in stream processing: {}", error.getMessage(), error);
                                StreamDeltaData deltaData = new StreamDeltaData();
                                deltaData.setType(TYPE_TEXT);
                                deltaData.setDelta("服务端处理失败");
                                sink.next(SseEventUtil.build("delta", deltaData));
                                fullTextRef[0] = fullTextRef[0] + deltaData.getDelta();
                            } else {
                                LinkedHashSet<String> pendingUrls = new LinkedHashSet<>(imageUrls);
                                pendingUrls.removeAll(emittedImageUrls);
                                List<ImageUrlItem> pendingImages = toImageItems(pendingUrls);
                                if (!pendingImages.isEmpty()) {
                                    StreamBlockData blockData = new StreamBlockData();
                                    blockData.setType(TYPE_IMAGE_LIST);
                                    blockData.setImages(pendingImages);
                                    sink.next(SseEventUtil.build("block", blockData));
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                        try {
                            persistAssistantContent(messageId, fullTextRef[0], images);
                        } catch (Throwable ignored) {
                        }
                        try {
                            assistant.setStatus(STATUS_DONE);
                            chatMessageMapper.updateById(assistant);
                        } catch (Throwable ignored) {
                        }
                        try {
                            StreamEndData endData = new StreamEndData();
                            endData.setMessageId(messageId);
                            sink.next(SseEventUtil.build("end", endData));
                        } catch (Throwable ignored) {
                        }
                        try {
                            sink.complete();
                        } catch (Throwable ignored) {
                        }
                    };

                    Flux<io.agentscope.core.agent.Event> upstream;
                    try {
                        upstream = supervisorAgent.stream(msg, safeSessionId, safeUserId);
                    } catch (Throwable e) {
                        streamError.set(e);
                        finalizeStream.run();
                        return;
                    }
                    Disposable disposable = upstream
                            .doOnNext(event -> {
                                if (interruptedForConfirm.get()) {
                                    return;
                                }
                                Msg output = event.getMessage();
                                if (event.getType() == io.agentscope.core.agent.EventType.REASONING
                                        && event.isLast()
                                        && output != null
                                        && output.hasContentBlocks(ToolUseBlock.class)) {
                                    for (ToolUseBlock tool : output.getContentBlocks(ToolUseBlock.class)) {
                                        if (tool == null || tool.getName() == null) {
                                            continue;
                                        }
                                        String toolName = tool.getName();
                                        if (!SENSITIVE_TOOLS.contains(toolName)) {
                                            continue;
                                        }
                                        if (toolConfirmationService.consumeApproval(safeSessionId, safeUserId, toolName)) {
                                            continue;
                                        }
                                        String confirmationId = toolConfirmationService.createPending(
                                                safeSessionId,
                                                safeUserId,
                                                toolName,
                                                triggerMessageId);
                                        StreamDeltaData deltaData = new StreamDeltaData();
                                        deltaData.setType(TYPE_TEXT);
                                        deltaData.setDelta("需要确认后才能执行操作：" + toolName);
                                        sink.next(SseEventUtil.build("delta", deltaData));
                                        fullTextRef[0] = fullTextRef[0] + deltaData.getDelta();
                                        StreamToolConfirmData confirmData = new StreamToolConfirmData();
                                        confirmData.setType(TYPE_TOOL_CONFIRM);
                                        confirmData.setConfirmationId(confirmationId);
                                        confirmData.setToolId(tool.getId());
                                        confirmData.setToolName(toolName);
                                        confirmData.setToolInput(tool.getInput());
                                        confirmData.setTriggerMessageId(triggerMessageId);
                                        confirmData.setMessage("是否允许执行该工具？");
                                        sink.next(SseEventUtil.build("block", confirmData));
                                        interruptedForConfirm.set(true);
                                        Disposable up = disposableRef.get();
                                        if (up != null && !up.isDisposed()) {
                                            up.dispose();
                                        }
                                        finalizeStream.run();
                                        return;
                                    }
                                }
                                boolean suppressDelta = false;
                                if (output != null && output.hasContentBlocks(ToolResultBlock.class)) {
                                    // 6) 优先处理工具结果 envelope：
                                    //    text      -> delta
                                    //    image_list-> block
                                    for (ToolResultBlock result : output.getContentBlocks(ToolResultBlock.class)) {
                                        ToolRenderPayload payload = extractToolRenderPayload(result);
                                        if (payload == null || payload.getRenderType() == null) {
                                            continue;
                                        }
                                        suppressDelta = true;
                                        if (TYPE_TEXT.equals(payload.getRenderType()) && payload.getText() != null && !payload.getText().isBlank()) {
                                            String toolText = payload.getText();
                                            fullTextRef[0] = fullTextRef[0] + toolText;
                                            StreamDeltaData deltaData = new StreamDeltaData();
                                            deltaData.setType(TYPE_TEXT);
                                            deltaData.setDelta(toolText);
                                            sink.next(SseEventUtil.build("delta", deltaData));
                                            continue;
                                        }
                                        if (TYPE_IMAGE_LIST.equals(payload.getRenderType()) && payload.getImageUrls() != null && !payload.getImageUrls().isEmpty()) {
                                            suppressDelta = true;
                                            imageUrls.addAll(payload.getImageUrls());
                                            LinkedHashSet<String> newUrls = new LinkedHashSet<>(payload.getImageUrls());
                                            newUrls.removeAll(emittedImageUrls);
                                            if (!newUrls.isEmpty()) {
                                                StreamBlockData blockData = new StreamBlockData();
                                                blockData.setType(TYPE_IMAGE_LIST);
                                                blockData.setImages(toImageItems(newUrls));
                                                sink.next(SseEventUtil.build("block", blockData));
                                                emittedImageUrls.addAll(newUrls);
                                            }
                                        }
                                    }
                                }
                                String chunkText = extractText(output);
                                if (chunkText != null && !chunkText.isBlank()) {
                                    String lastRawText = rawTextRef[0];
                                    String nextRawText;
                                    String delta;
                                    if (!lastRawText.isBlank() && chunkText.startsWith(lastRawText)) {
                                        nextRawText = chunkText;
                                        delta = chunkText.substring(lastRawText.length());
                                    } else {
                                        delta = chunkText;
                                        nextRawText = lastRawText + delta;
                                    }
                                    rawTextRef[0] = nextRawText;
                                    if (!delta.isBlank()) {
                                        // 7) 对 delta 做 JSON 泄漏过滤，防止 {"render_type":"image..."} 半截透传
                                        String safeDelta = sanitizeDeltaForClient(delta, jsonLeakFilterState);
                                        boolean shouldSuppress = suppressDelta
                                                || shouldSuppressEnvelopeText(safeDelta)
                                                || shouldSuppressEnvelopeText(nextRawText);
                                        if (!shouldSuppress && safeDelta != null && !safeDelta.isBlank()) {
                                            StreamDeltaData deltaData = new StreamDeltaData();
                                            deltaData.setType(TYPE_TEXT);
                                            deltaData.setDelta(safeDelta);
                                            sink.next(SseEventUtil.build("delta", deltaData));
                                            fullTextRef[0] = fullTextRef[0] + safeDelta;
                                        }
                                    }
                                    imageUrls.addAll(extractImageUrls(nextRawText));
                                }
                            })
                            // 8) 超时兜底，避免模型流无结束导致前端一直 loading
                            .doOnError(streamError::set)
                            .doFinally(signalType -> {
                                finalizeStream.run();
                            })
                            .subscribe(ignored -> {
                            }, ignored -> {
                            });
                    disposableRef.set(disposable);
                    sink.onCancel(() -> {
                        if (disposable.isDisposed()) {
                            return;
                        }
                    });
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public ToolConfirmResponse confirmTool(ToolConfirmRequest request) {
        ToolConfirmationService.PendingTool pending = toolConfirmationService.getPending(request.getConfirmationId());
        if (pending == null) {
            ToolConfirmResponse response = new ToolConfirmResponse();
            response.setStatus("not_found");
            return response;
        }
        if (!pending.getSessionId().equals(request.getSessionId())) {
            ToolConfirmResponse response = new ToolConfirmResponse();
            response.setStatus("not_found");
            return response;
        }
        boolean approved = request.getApproved() != null && request.getApproved();
        if (approved) {
            toolConfirmationService.approve(request.getConfirmationId());
        } else {
            toolConfirmationService.deny(request.getConfirmationId());
        }
        ToolConfirmResponse response = new ToolConfirmResponse();
        response.setStatus("ok");
        response.setTriggerMessageId(pending.getTriggerMessageId());
        response.setToolName(pending.getToolName());
        return response;
    }

    private void persistContentBlocks(String messageId, List<ChatSendContentBlock> contentBlocks) {
        if (contentBlocks == null || contentBlocks.isEmpty()) {
            return;
        }
        for (int i = 0; i < contentBlocks.size(); i++) {
            ChatSendContentBlock block = contentBlocks.get(i);
            MessageContent content = new MessageContent();
            content.setMessageId(messageId);
            content.setIdx(i);
            content.setType(block.getType());
            content.setData(toJson(block));
            messageContentMapper.insert(content);
        }
    }

    private void persistAssistantContent(String messageId, String text, List<ImageUrlItem> images) {
        List<ChatSendContentBlock> blocks = new ArrayList<>();
        blocks.add(textBlock(text));
        if (images != null && !images.isEmpty()) {
            ChatSendContentBlock imageBlock = new ChatSendContentBlock();
            imageBlock.setType(TYPE_IMAGE_LIST);
            imageBlock.setImages(images);
            blocks.add(imageBlock);
        }
        persistContentBlocks(messageId, blocks);
    }

    private List<ChatSendContentBlock> normalizeBlocks(List<ChatSendContentBlock> blocks) {
        if (blocks == null) {
            return List.of();
        }
        List<ChatSendContentBlock> normalized = new ArrayList<>();
        for (ChatSendContentBlock block : blocks) {
            if (block == null || block.getType() == null) {
                continue;
            }
            if (TYPE_TEXT.equals(block.getType())) {
                ChatSendContentBlock textBlock = new ChatSendContentBlock();
                textBlock.setType(TYPE_TEXT);
                textBlock.setText(block.getText() == null ? "" : block.getText());
                normalized.add(textBlock);
                continue;
            }
            if (TYPE_IMAGE_LIST.equals(block.getType())) {
                ChatSendContentBlock imageBlock = new ChatSendContentBlock();
                imageBlock.setType(TYPE_IMAGE_LIST);
                imageBlock.setImages(block.getImages() == null ? List.of() : block.getImages());
                normalized.add(imageBlock);
            }
        }
        return normalized;
    }

    private List<ChatSendContentBlock> findUserBlocks(String sessionId, String userId, String triggerMessageId) {
        String candidateId = triggerMessageId;
        if (candidateId == null || candidateId.isBlank()) {
            LambdaQueryWrapper<ChatMessage> messageWrapper = new LambdaQueryWrapper<>();
            messageWrapper.eq(ChatMessage::getSessionId, sessionId)
                    .eq(ChatMessage::getUserId, userId)
                    .eq(ChatMessage::getRole, ROLE_USER)
                    .orderByDesc(ChatMessage::getSeq)
                    .last("limit 1");
            ChatMessage latestUserMessage = chatMessageMapper.selectOne(messageWrapper);
            if (latestUserMessage == null) {
                return List.of();
            }
            candidateId = latestUserMessage.getMessageId();
        }
        LambdaQueryWrapper<MessageContent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageContent::getMessageId, candidateId)
                .orderByAsc(MessageContent::getIdx);
        List<MessageContent> contents = messageContentMapper.selectList(wrapper);
        if (contents == null || contents.isEmpty()) {
            return List.of();
        }
        List<ChatSendContentBlock> blocks = new ArrayList<>();
        for (MessageContent content : contents) {
            ChatSendContentBlock block = parseContentBlock(content);
            if (block != null) {
                blocks.add(block);
            }
        }
        return normalizeBlocks(blocks);
    }

    private ChatSendContentBlock parseContentBlock(MessageContent content) {
        if (content == null || content.getData() == null || content.getData().isBlank()) {
            return null;
        }
        try {
            ChatSendContentBlock block = objectMapper.readValue(content.getData(), ChatSendContentBlock.class);
            if (block.getType() == null || block.getType().isBlank()) {
                block.setType(content.getType());
            }
            return block;
        } catch (Exception ignored) {
            return null;
        }
    }

    private ChatSendContentBlock textBlock(String text) {
        ChatSendContentBlock block = new ChatSendContentBlock();
        block.setType(TYPE_TEXT);
        block.setText(text);
        return block;
    }

    private ImageUrlItem image(String url) {
        ImageUrlItem item = new ImageUrlItem();
        item.setUrl(url);
        return item;
    }

    private String toPromptText(List<ChatSendContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ChatSendContentBlock block : blocks) {
            if (block == null || block.getType() == null) {
                continue;
            }
            if (TYPE_TEXT.equals(block.getType()) && block.getText() != null && !block.getText().isBlank()) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(block.getText().trim());
            }
            if (TYPE_IMAGE_LIST.equals(block.getType()) && block.getImages() != null && !block.getImages().isEmpty()) {
                List<String> urls = new ArrayList<>();
                for (ImageUrlItem item : block.getImages()) {
                    if (item != null && item.getUrl() != null && !item.getUrl().isBlank()) {
                        urls.add(item.getUrl().trim());
                    }
                }
                if (!urls.isEmpty()) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }
                    builder.append("图片列表：").append(String.join("，", urls));
                }
            }
        }
        return builder.toString();
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock) {
                String text = ((TextBlock) block).getText();
                if (text != null) {
                    builder.append(text);
                }
            }
        }
        return builder.toString();
    }

    private List<String> extractImageUrls(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        Matcher mdMatcher = MARKDOWN_IMAGE_PATTERN.matcher(text);
        while (mdMatcher.find()) {
            String url = mdMatcher.group("url");
            if (url != null && !url.isBlank()) {
                urls.add(url.strip());
            }
        }
        Matcher urlMatcher = IMAGE_URL_PATTERN.matcher(text);
        while (urlMatcher.find()) {
            String url = urlMatcher.group();
            if (url != null && !url.isBlank()) {
                urls.add(url.strip());
            }
        }
        return new ArrayList<>(urls);
    }

    private ToolRenderPayload extractToolRenderPayload(ToolResultBlock result) {
        if (result == null || result.getOutput() == null || result.getOutput().isEmpty()) {
            return null;
        }
        for (ContentBlock output : result.getOutput()) {
            if (!(output instanceof TextBlock)) {
                continue;
            }
            String raw = ((TextBlock) output).getText();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            ToolRenderPayload payload = parseToolRenderPayloadFromRaw(raw);
            if (payload != null && payload.getRenderType() != null) {
                return payload;
            }
        }
        return null;
    }

    private ToolRenderPayload parseToolRenderPayloadFromRaw(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            String renderType = root.path("render_type").asText("");
            if (renderType.isBlank()) {
                return null;
            }
            ToolRenderPayload payload = new ToolRenderPayload();
            payload.setRenderType(renderType.trim());
            JsonNode dataNode = root.path("data");
            if (TYPE_TEXT.equals(payload.getRenderType())) {
                if (dataNode.isTextual()) {
                    payload.setText(dataNode.asText(""));
                    return payload;
                }
                if (dataNode.isObject()) {
                    payload.setText(dataNode.path("text").asText(""));
                    return payload;
                }
                payload.setText(root.path("text").asText(""));
                return payload;
            }
            if (TYPE_IMAGE_LIST.equals(payload.getRenderType())) {
                LinkedHashSet<String> urls = new LinkedHashSet<>();
                if (dataNode.isArray()) {
                    for (JsonNode node : dataNode) {
                        collectImageUrl(node, urls);
                    }
                } else if (dataNode.isObject() && dataNode.path("images").isArray()) {
                    for (JsonNode node : dataNode.path("images")) {
                        collectImageUrl(node, urls);
                    }
                } else if (root.path("images").isArray()) {
                    for (JsonNode node : root.path("images")) {
                        collectImageUrl(node, urls);
                    }
                }
                payload.setImageUrls(new ArrayList<>(urls));
                return payload;
            }
            return payload;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void collectImageUrl(JsonNode node, Set<String> urls) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        String avatar = cleanUrl(node.path("avatar").asText(""));
        if (!avatar.isBlank()) {
            urls.add(avatar);
        }
        String url = cleanUrl(node.path("url").asText(""));
        if (!url.isBlank()) {
            urls.add(url);
        }
        String imageUrl = cleanUrl(node.path("imageUrl").asText(""));
        if (!imageUrl.isBlank()) {
            urls.add(imageUrl);
        }
    }

    private boolean shouldSuppressEnvelopeText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.replace("\n", "").replace("\r", "");
        if (normalized.contains("\"render_type\":\"image")) {
            return true;
        }
        if (normalized.contains("{\"render_type\":\"image")) {
            return true;
        }
        if (normalized.contains("\"render_type\"")) {
            if (normalized.contains("\"image_list\"")) {
                return true;
            }
            if (normalized.contains("\"data\"") && normalized.contains("\"images\"")) {
                return true;
            }
        }
        if (normalized.contains("render_type") && normalized.contains("image_list")) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(text);
            return TYPE_IMAGE_LIST.equals(root.path("render_type").asText(""));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String cleanUrl(String url) {
        if (url == null) {
            return "";
        }
        return url.replace("`", "").trim();
    }

    private String sanitizeDeltaForClient(String delta, JsonLeakFilterState state) {
        if (delta == null || delta.isEmpty()) {
            return "";
        }
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < delta.length(); i++) {
            char c = delta.charAt(i);
            if (state.suppressingJsonObject) {
                consumeSuppressedJsonChar(c, state);
                continue;
            }
            if (state.waitingQuoteAfterBrace) {
                if (c == '"') {
                    state.waitingQuoteAfterBrace = false;
                    state.suppressingJsonObject = true;
                    state.jsonDepth = 1;
                    state.inString = true;
                    state.escaped = false;
                    continue;
                }
                output.append('{');
                state.waitingQuoteAfterBrace = false;
            }
            if (c == '{') {
                state.waitingQuoteAfterBrace = true;
                continue;
            }
            output.append(c);
        }
        return output.toString();
    }

    private void consumeSuppressedJsonChar(char c, JsonLeakFilterState state) {
        if (state.escaped) {
            state.escaped = false;
            return;
        }
        if (state.inString && c == '\\') {
            state.escaped = true;
            return;
        }
        if (c == '"') {
            state.inString = !state.inString;
            return;
        }
        if (!state.inString) {
            if (c == '{') {
                state.jsonDepth++;
                return;
            }
            if (c == '}') {
                state.jsonDepth--;
                if (state.jsonDepth <= 0) {
                    state.suppressingJsonObject = false;
                    state.jsonDepth = 0;
                    state.inString = false;
                    state.escaped = false;
                }
            }
        }
    }

    private static class ToolRenderPayload {
        private String renderType;
        private String text;
        private List<String> imageUrls;

        public String getRenderType() {
            return renderType;
        }

        public void setRenderType(String renderType) {
            this.renderType = renderType;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public List<String> getImageUrls() {
            return imageUrls;
        }

        public void setImageUrls(List<String> imageUrls) {
            this.imageUrls = imageUrls;
        }
    }

    private static class JsonLeakFilterState {
        private boolean waitingQuoteAfterBrace;
        private boolean suppressingJsonObject;
        private int jsonDepth;
        private boolean inString;
        private boolean escaped;
    }

    private List<ImageUrlItem> toImageItems(Set<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        List<ImageUrlItem> items = new ArrayList<>();
        for (String url : urls) {
            String cleaned = cleanUrl(url);
            if (cleaned.isBlank()) {
                continue;
            }
            items.add(image(cleaned));
        }
        return items;
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        return sessionId;
    }

    private String normalizeUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return DEFAULT_USER_ID;
        }
        return userId;
    }

    private int nextSeq(String sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getSeq)
                .last("limit 1");
        ChatMessage latest = chatMessageMapper.selectOne(wrapper);
        if (latest == null || latest.getSeq() == null) {
            return 1;
        }
        return latest.getSeq() + 1;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

}
