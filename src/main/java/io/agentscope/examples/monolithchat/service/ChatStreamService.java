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
import io.agentscope.examples.monolithchat.dto.RecommendTagItem;
import io.agentscope.examples.monolithchat.dto.StreamBlockData;
import io.agentscope.examples.monolithchat.dto.StreamDeltaData;
import io.agentscope.examples.monolithchat.dto.StreamEndData;
import io.agentscope.examples.monolithchat.dto.StreamTagListData;
import io.agentscope.examples.monolithchat.dto.StreamStartData;
import io.agentscope.examples.monolithchat.dto.StreamToolConfirmData;
import io.agentscope.examples.monolithchat.dto.TagSelectRequest;
import io.agentscope.examples.monolithchat.dto.TagSelectResponse;
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
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;
import reactor.core.Disposable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private static final String TYPE_TAG_LIST = "tag_list";
    private static final String TYPE_TOOL_CONFIRM = "tool_confirm";
    private static final String TYPE_PACKAGE_LIST = "package_list";
    private static final String TYPE_DM_CARD = "dm_card";
    private static final String TYPE_RECOMMEND_LIST = "recommend_list";
    private static final String DEFAULT_USER_ID = "55134";
    private static final String DEFAULT_TOOL_CONFIRM_MESSAGE = "是否允许执行该工具？";
    private static final String DEFAULT_TAG_SELECT_MESSAGE = "请选择你喜欢的用户标签，我们将为你推荐";
    private static final List<String> SENSITIVE_TOOLS = List.of("delete_file", "send_email");
    private static final Map<String, String> TEXT_DATA_BLOCK_TYPE_MAPPING = Map.of(
            "packageList", TYPE_PACKAGE_LIST,
            "dmCard", TYPE_DM_CARD
    );
    private static final Pattern MARKDOWN_IMAGE_PATTERN =
            Pattern.compile("!\\[[^\\]]*\\]\\((?<url>[^)\\s]+)\\)");
    private static final Pattern IMAGE_URL_PATTERN =
            Pattern.compile("(?i)\\bhttps?://[^\\s)\\]}>]+\\.(png|jpe?g|gif|webp)(\\?[^\\s)\\]}>]+)?");

    private final ChatMessageMapper chatMessageMapper;
    private final MessageContentMapper messageContentMapper;
    private final ObjectMapper objectMapper;
    private final SupervisorAgent supervisorAgent;
    private final ToolConfirmationService toolConfirmationService;
    private final RecommendTagTaskService recommendTagTaskService;
    private final RedisShortTermMemoryService redisShortTermMemoryService;
    private final UserLongTermMemoryService userLongTermMemoryService;
    private final SemanticMemoryEsService semanticMemoryEsService;

    public ChatStreamService(
            ChatMessageMapper chatMessageMapper,
            MessageContentMapper messageContentMapper,
            ObjectMapper objectMapper,
            SupervisorAgent supervisorAgent,
            ToolConfirmationService toolConfirmationService,
            RecommendTagTaskService recommendTagTaskService,
            RedisShortTermMemoryService redisShortTermMemoryService,
            UserLongTermMemoryService userLongTermMemoryService,
            SemanticMemoryEsService semanticMemoryEsService) {
        this.chatMessageMapper = chatMessageMapper;
        this.messageContentMapper = messageContentMapper;
        this.objectMapper = objectMapper;
        this.supervisorAgent = supervisorAgent;
        this.toolConfirmationService = toolConfirmationService;
        this.recommendTagTaskService = recommendTagTaskService;
        this.redisShortTermMemoryService = redisShortTermMemoryService;
        this.userLongTermMemoryService = userLongTermMemoryService;
        this.semanticMemoryEsService = semanticMemoryEsService;
    }

    /**
     * 持久化用户输入消息并返回 trigger messageId。
     */
    public ChatSendResponse send(ChatSendRequest request) {
        String sessionId = normalizeSessionId(request.getSessionId());
        String userId = normalizeUserId(request.getUserId());
        String messageId = UUID.randomUUID().toString();
        int seq = nextSeq(sessionId);
        ChatMessage message = new ChatMessage();
        message.setMessageId(messageId);
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(ROLE_USER);
        message.setStatus(STATUS_DONE);
        message.setSeq(seq);
        message.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(message);
        List<ChatSendContentBlock> normalized = normalizeBlocks(request.getContent());
        persistContentBlocks(messageId, normalized);
        String userText = toPromptText(normalized);
        redisShortTermMemoryService.appendUserMessage(sessionId, userId, messageId, userText);
        userLongTermMemoryService.recordBehavior(userId, sessionId, "user_input", clip(userText, 400), messageId);
        semanticMemoryEsService.save(userId, sessionId, "user_input", clip(userText, 600), 0.4, messageId);
        ChatSendResponse response = new ChatSendResponse();
        response.setMessageId(messageId);
        response.setSessionId(sessionId);
        response.setStatus(STATUS_DONE);
        return response;
    }

    /**
     * 对外提供流式对话输出，统一编排：上下文装配、Agent 调用、工具结果分发、落库与收尾。
     */
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
                    RecommendTagItem selectedTag = recommendTagTaskService.consumeSelectedTag(safeSessionId, safeUserId, triggerMessageId);
                    String userPrompt = toPromptText(inputBlocks);
                    if (selectedTag != null) {
                        String label = selectedTag.getLabel() == null ? selectedTag.getId() : selectedTag.getLabel();
                        String hint = selectedTag.getPromptHint() == null ? "" : selectedTag.getPromptHint();
                        userPrompt = userPrompt
                                + "\n用户已选择推荐标签：" + label
                                + "\n请基于该标签直接给出推荐结果，不要再次调用标签推荐工具。"
                                + (hint.isBlank() ? "" : ("\n标签约束：" + hint));
                        userLongTermMemoryService.recordTagSelection(safeUserId, "tag_select", selectedTag);
                        userLongTermMemoryService.recordBehavior(safeUserId, safeSessionId, "tag_select", label, triggerMessageId);
                        semanticMemoryEsService.save(safeUserId, safeSessionId, "tag_preference", "用户选择推荐标签：" + label, 0.9, triggerMessageId);
                    }
                    userPrompt = composeMemoryAwarePrompt(safeSessionId, safeUserId, userPrompt);
                    Msg msg = Msg.builder()
                            .role(MsgRole.USER)
                            .content(TextBlock.builder().text(userPrompt).build())
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
                            redisShortTermMemoryService.appendAssistantMessage(safeSessionId, safeUserId, messageId, fullTextRef[0]);
                            userLongTermMemoryService.recordBehavior(safeUserId, safeSessionId, "assistant_reply", clip(fullTextRef[0], 500), triggerMessageId);
                            semanticMemoryEsService.save(safeUserId, safeSessionId, "assistant_reply", clip(fullTextRef[0], 800), 0.6, triggerMessageId);
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
                                        confirmData.setMessage(DEFAULT_TOOL_CONFIRM_MESSAGE);
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
                                        PayloadHandleResult resultState = handleToolPayload(
                                                payload,
                                                sink,
                                                fullTextRef,
                                                interruptedForConfirm,
                                                disposableRef,
                                                finalizeStream,
                                                safeSessionId,
                                                safeUserId,
                                                triggerMessageId);
                                        suppressDelta = suppressDelta || resultState.isSuppressDelta();
                                        if (resultState.isFinalized()) {
                                            return;
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

    /**
     * 工具确认回调：记录用户是否批准敏感工具执行，并返回可继续对话的 triggerMessageId。
     */
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

    /**
     * 标签选择回调：保存用户选择结果并返回下一轮触发消息标识。
     */
    public TagSelectResponse selectTag(TagSelectRequest request) {
        String safeUserId = normalizeUserId(request.getUserId());
        String safeSessionId = normalizeSessionId(request.getSessionId());
        boolean ok = recommendTagTaskService.selectTag(
                request.getTaskId(),
                safeSessionId,
                safeUserId,
                request.getTagId());
        TagSelectResponse response = new TagSelectResponse();
        if (!ok) {
            response.setStatus("invalid");
            return response;
        }
        RecommendTagTaskService.TaskState task = recommendTagTaskService.getByTaskId(request.getTaskId());
        response.setStatus("ok");
        response.setTaskId(request.getTaskId());
        response.setTagId(request.getTagId());
        response.setTriggerMessageId(task == null ? null : task.getTriggerMessageId());
        if (task != null && task.getTags() != null) {
            RecommendTagItem selected = task.getTags().stream()
                    .filter(item -> item != null && request.getTagId() != null && request.getTagId().equals(item.getId()))
                    .findFirst()
                    .orElse(null);
            if (selected != null) {
                String label = selected.getLabel() == null || selected.getLabel().isBlank() ? selected.getId() : selected.getLabel();
                userLongTermMemoryService.recordTagSelection(safeUserId, "tag_select_api", selected);
                userLongTermMemoryService.recordBehavior(safeUserId, safeSessionId, "tag_select", label, task.getTriggerMessageId());
                semanticMemoryEsService.save(safeUserId, safeSessionId, "tag_preference", "用户选择推荐标签：" + label, 0.9, task.getTriggerMessageId());
            }
        }
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

    private String composeMemoryAwarePrompt(String sessionId, String userId, String currentInput) {
        List<String> shortTerm = redisShortTermMemoryService.loadRecentTextContext(sessionId, userId, 12);
        String longTermTags = userLongTermMemoryService.loadTagSummary(userId, 10);
        String longTermBehavior = userLongTermMemoryService.loadBehaviorSummary(userId, 10);
        List<String> semanticMemories = semanticMemoryEsService.search(userId, currentInput, 5);
        StringBuilder builder = new StringBuilder();
        if (!shortTerm.isEmpty()) {
            builder.append("【短期记忆】\n");
            for (String item : shortTerm) {
                builder.append("- ").append(item).append('\n');
            }
        }
        if (longTermTags != null && !longTermTags.isBlank()) {
            builder.append("【长期标签记忆】\n");
            builder.append(longTermTags).append('\n');
        }
        if (longTermBehavior != null && !longTermBehavior.isBlank()) {
            builder.append("【长期行为记忆】\n");
            builder.append(longTermBehavior).append('\n');
        }
        if (semanticMemories != null && !semanticMemories.isEmpty()) {
            builder.append("【长期语义记忆】\n");
            for (String memory : semanticMemories) {
                if (memory == null || memory.isBlank()) {
                    continue;
                }
                builder.append("- ").append(memory.strip()).append('\n');
            }
        }
        builder.append("【用户当前输入】\n").append(currentInput == null ? "" : currentInput);
        return builder.toString();
    }

    private String clip(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String value = text.strip();
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLen));
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

    /**
     * 处理工具 envelope 输出并转换为前端可消费的事件。
     * 返回值用于控制主循环是否抑制 delta、是否立刻结束当前流。
     */
    private PayloadHandleResult handleToolPayload(
            ToolRenderPayload payload,
            FluxSink<ServerSentEvent<Object>> sink,
            String[] fullTextRef,
            AtomicBoolean interruptedForConfirm,
            AtomicReference<Disposable> disposableRef,
            Runnable finalizeStream,
            String sessionId,
            String userId,
            String triggerMessageId) {
        if (TYPE_TEXT.equals(payload.getRenderType()) && payload.getText() != null && !payload.getText().isBlank()) {
            emitTextDelta(payload.getText(), sink, fullTextRef);
            emitMappedTextDataBlocks(payload.getDataMap(), sink);
            return PayloadHandleResult.suppressOnly();
        }
        if (TYPE_IMAGE_LIST.equals(payload.getRenderType()) && payload.getImageUrls() != null && !payload.getImageUrls().isEmpty()) {
//            imageUrls.addAll(payload.getImageUrls());
//            emitImageListBlock(payload.getImageUrls(), sink);
            emitRecommendListBlock(payload.getDataMap(), sink);
            interruptedForConfirm.set(true);
            Disposable up = disposableRef.get();
            if (up != null && !up.isDisposed()) {
                up.dispose();
            }
            finalizeStream.run();
            return PayloadHandleResult.suppressAndFinalize();
        }
        if (TYPE_TAG_LIST.equals(payload.getRenderType()) && payload.getTags() != null && !payload.getTags().isEmpty()) {
            RecommendTagTaskService.TaskState state = recommendTagTaskService.createTask(
                    sessionId,
                    userId,
                    triggerMessageId,
                    payload.getTitle(),
                    payload.getTags());
            StreamTagListData tagData = new StreamTagListData();
            tagData.setType(TYPE_TAG_LIST);
            tagData.setTaskId(state.getTaskId());
            tagData.setTitle(payload.getTitle());
            tagData.setMessage(DEFAULT_TAG_SELECT_MESSAGE);
            tagData.setTags(payload.getTags());
            tagData.setTriggerMessageId(triggerMessageId);
            sink.next(SseEventUtil.build("block", tagData));
            interruptedForConfirm.set(true);
            Disposable up = disposableRef.get();
            if (up != null && !up.isDisposed()) {
                up.dispose();
            }
            finalizeStream.run();
            return PayloadHandleResult.suppressAndFinalize();
        }
        return PayloadHandleResult.none();
    }

    /**
     * 下发 text 增量并同步更新可落库的完整文本缓存。
     */
    private void emitTextDelta(String text, FluxSink<ServerSentEvent<Object>> sink, String[] fullTextRef) {
        StreamDeltaData deltaData = new StreamDeltaData();
        deltaData.setType(TYPE_TEXT);
        deltaData.setDelta(text);
        sink.next(SseEventUtil.build("delta", deltaData));
        fullTextRef[0] = fullTextRef[0] + text;
    }

    /**
     * 将 text 类型中附带的结构化数据，按 key->blockType 映射下发为 block 事件。
     */
    private void emitMappedTextDataBlocks(Map<String, Object> dataMap, FluxSink<ServerSentEvent<Object>> sink) {
        if (dataMap == null || dataMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> mapping : TEXT_DATA_BLOCK_TYPE_MAPPING.entrySet()) {
            Object value = dataMap.get(mapping.getKey());
            if (value == null) {
                continue;
            }
            StreamBlockData block = new StreamBlockData();
            block.setType(mapping.getValue());
            if (TYPE_PACKAGE_LIST.equals(mapping.getValue())) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("packageList", value);
                block.setData(data);
            } else {
                block.setData(value);
            }
            sink.next(SseEventUtil.build("block", block));
        }
    }

    /**
     * 下发图片列表 block，并通过 emittedImageUrls 去重，避免重复推送。
     */
    private void emitImageListBlock(List<String> payloadImageUrls, FluxSink<ServerSentEvent<Object>> sink) {
        StreamBlockData blockData = new StreamBlockData();
        blockData.setType(TYPE_IMAGE_LIST);
//        blockData.setImages(payloadImageUrls);
        sink.next(SseEventUtil.build("block", blockData));
//        emittedImageUrls.addAll(newUrls);
    }

    /**
     * 当图片数据具备推荐卡片结构时，额外下发 recommend_list block。
     */
    private void emitRecommendListBlock(Map<String, Object> dataMap, FluxSink<ServerSentEvent<Object>> sink) {
        if (dataMap == null) {
            return;
        }
        Object images = dataMap.get("images");
        if (!(images instanceof List) || !hasRecommendShape((List<?>) images)) {
            return;
        }
        StreamBlockData recommendBlock = new StreamBlockData();
        recommendBlock.setType(TYPE_RECOMMEND_LIST);
        recommendBlock.setMessage("选择你心仪的女生");
        recommendBlock.setData(images);
        sink.next(SseEventUtil.build("block", recommendBlock));
    }

    /**
     * 从 ToolResultBlock 中提取第一个可识别的工具渲染 payload。
     */
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

    /**
     * 解析工具原始输出字符串，兼容 text/image_list/tag_list 三类 envelope。
     */
    private ToolRenderPayload parseToolRenderPayloadFromRaw(String raw) {
        try {
            JsonNode root = parseJsonLoose(raw);
            if (root == null) {
                return null;
            }
            String renderType = root.path("render_type").asText("");
            if (renderType.isBlank()) {
                return null;
            }
            ToolRenderPayload payload = new ToolRenderPayload();
            payload.setRenderType(renderType.trim());
            JsonNode dataNode = root.path("data");
            JsonNode normalizedDataNode = unwrapDataNode(dataNode);
            if (dataNode.isObject()) {
                payload.setDataMap(objectMapper.convertValue(dataNode, Map.class));
            } else if (normalizedDataNode != null && normalizedDataNode.isObject()) {
                payload.setDataMap(objectMapper.convertValue(normalizedDataNode, Map.class));
            }
            if (TYPE_TEXT.equals(payload.getRenderType())) {
                if (normalizedDataNode != null && normalizedDataNode.isTextual()) {
                    payload.setText(normalizedDataNode.asText(""));
                    return payload;
                }
                if (normalizedDataNode != null && normalizedDataNode.isObject()) {
                    payload.setText(normalizedDataNode.path("text").asText(""));
                    return payload;
                }
                payload.setText(root.path("text").asText(""));
                return payload;
            }
            if (TYPE_IMAGE_LIST.equals(payload.getRenderType())) {
                LinkedHashSet<String> urls = new LinkedHashSet<>();
                if (normalizedDataNode != null && normalizedDataNode.isArray()) {
                    for (JsonNode node : normalizedDataNode) {
                        collectImageUrl(node, urls);
                    }
                } else if (normalizedDataNode != null && normalizedDataNode.isObject() && normalizedDataNode.path("images").isArray()) {
                    for (JsonNode node : normalizedDataNode.path("images")) {
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
            if (TYPE_TAG_LIST.equals(payload.getRenderType())) {
                List<RecommendTagItem> tags = new ArrayList<>();
                JsonNode tagsNode = normalizedDataNode == null ? null : normalizedDataNode.path("tags");
                if (tagsNode == null || !tagsNode.isArray()) {
                    tagsNode = root.path("tags");
                }
                if (tagsNode.isArray()) {
                    for (JsonNode node : tagsNode) {
                        String id = node.path("id").asText("");
                        String label = node.path("label").asText("");
                        if (id.isBlank()) {
                            continue;
                        }
                        RecommendTagItem item = new RecommendTagItem();
                        item.setId(id.trim());
                        item.setLabel(label.isBlank() ? id.trim() : label.trim());
                        item.setPromptHint(node.path("promptHint").asText(""));
                        tags.add(item);
                    }
                }
                String title = normalizedDataNode == null ? "" : normalizedDataNode.path("title").asText("");
                if (title == null || title.isBlank()) {
                    title = root.path("title").asText("");
                }
                payload.setTitle(title);
                payload.setTags(tags);
                return payload;
            }
            return payload;
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode unwrapDataNode(JsonNode dataNode) {
        if (dataNode == null || dataNode.isMissingNode() || dataNode.isNull()) {
            return dataNode;
        }
        if (!dataNode.isTextual()) {
            return dataNode;
        }
        String text = dataNode.asText("");
        if (text.isBlank()) {
            return dataNode;
        }
        JsonNode parsed = parseJsonLoose(text);
        return parsed == null ? dataNode : parsed;
    }

    /**
     * 宽松 JSON 解析：支持 markdown code fence 和文本中嵌入 JSON 子串。
     */
    private JsonNode parseJsonLoose(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.strip();
        if (text.startsWith("```")) {
            text = text.replace("```json", "").replace("```JSON", "").replace("```", "").strip();
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception ignored) {
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String candidate = text.substring(start, end + 1);
            try {
                return objectMapper.readTree(candidate);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean hasRecommendShape(List<?> images) {
        if (images == null || images.isEmpty()) {
            return false;
        }
        Object first = images.get(0);
        if (!(first instanceof Map)) {
            return false;
        }
        Map<?, ?> map = (Map<?, ?>) first;
        return map.containsKey("userId") || map.containsKey("nickname") || map.containsKey("avatar");
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
            if (normalized.contains("\"tag_list\"")) {
                return true;
            }
            if (normalized.contains("\"data\"") && normalized.contains("\"images\"")) {
                return true;
            }
        }
        if (normalized.contains("render_type") && (normalized.contains("image_list") || normalized.contains("tag_list"))) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(text);
            String rt = root.path("render_type").asText("");
            return TYPE_IMAGE_LIST.equals(rt) || TYPE_TAG_LIST.equals(rt);
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
        private String title;
        private List<RecommendTagItem> tags;
        private Map<String, Object> dataMap;

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

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public List<RecommendTagItem> getTags() {
            return tags;
        }

        public void setTags(List<RecommendTagItem> tags) {
            this.tags = tags;
        }

        public Map<String, Object> getDataMap() {
            return dataMap;
        }

        public void setDataMap(Map<String, Object> dataMap) {
            this.dataMap = dataMap;
        }
    }

    private static class PayloadHandleResult {
        private final boolean suppressDelta;
        private final boolean finalized;

        private PayloadHandleResult(boolean suppressDelta, boolean finalized) {
            this.suppressDelta = suppressDelta;
            this.finalized = finalized;
        }

        public static PayloadHandleResult none() {
            return new PayloadHandleResult(false, false);
        }

        public static PayloadHandleResult suppressOnly() {
            return new PayloadHandleResult(true, false);
        }

        public static PayloadHandleResult suppressAndFinalize() {
            return new PayloadHandleResult(true, true);
        }

        public boolean isSuppressDelta() {
            return suppressDelta;
        }

        public boolean isFinalized() {
            return finalized;
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
