/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.agentscope.examples.monolithchat.agentscope;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.studio.StudioManager;
import io.agentscope.core.studio.StudioMessageHook;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.examples.monolithchat.dto.UserContext;
import io.agentscope.examples.monolithchat.entity.User;
import io.agentscope.examples.monolithchat.mapper.UserMapper;
import io.agentscope.examples.monolithchat.service.PromptComposer;
import io.agentscope.examples.monolithchat.service.StudioHookFactory;
import io.agentscope.examples.monolithchat.tools.UserTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SupervisorAgent wrapper that creates a new ReActAgent instance for each request.
 * This ensures complete isolation between requests without maintaining conversation context.
 */
@Service
public class SupervisorAgent {

    @Autowired
    private UserMapper userMapper;

    @Value("${dashscope.api-key:${DASHSCOPE_API_KEY:}}")
    private String apiKey;

    @Value("${dashscope.model-name:qwen-plus}")
    private String modelName;

    private static final Logger logger = LoggerFactory.getLogger(SupervisorAgent.class);

    @Autowired
    private PromptComposer promptComposer;

    @Autowired
    private  StudioHookFactory studioHookFactory;

    @Autowired
    private UserTool userTool;

    private final Map<String, Memory> memoryBySession = new ConcurrentHashMap<>();

    /**
     * Stream method that handles user messages by creating a new agent for each request.
     *
     * @param msg the user message
     * @return Flux of Events from the agent
     */
    public Flux<Event> stream(Msg msg, String sessionId, String userId) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(userTool);
        ToolExecutionContext context = ToolExecutionContext.builder()
                .register(getUserContext(userId))
                .build();
        String sysPrompt = promptComposer.compose(userId);
        String key = sessionKey(sessionId, userId);
        Memory memory = memoryBySession.computeIfAbsent(key, k -> new InMemoryMemory());
        ReActAgent agent = createAgent(toolkit, memory, sysPrompt, sessionId, userId, context);
        AtomicBoolean retried = new AtomicBoolean(false);
        return agent.stream(msg)
                .onErrorResume(error -> {
                    if (!isPendingToolCallError(error) || !retried.compareAndSet(false, true)) {
                        return Flux.error(error);
                    }
                    try {
                        memory.clear();
                        logger.warn("InMemoryMemory cleared and retrying due to pending tool calls: {}", sessionId);
                    } catch (Throwable ignored) {
                    }
                    ReActAgent recovered = createAgent(toolkit, memory, sysPrompt, sessionId, userId, context);
                    return recovered.stream(msg);
                })
                .doFinally(signalType -> logger.info("Stream terminated with signal: {}, session: {}", signalType, sessionId));
    }

    private boolean isPendingToolCallError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("pending tool calls exist")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String sessionKey(String sessionId, String userId) {
        return sessionId + ":" + userId;
    }

    private UserContext getUserContext(String userId) {
        UserContext userContext = new UserContext();
        User user = userMapper.selectById(userId);
        userContext.setUserId(userId);
        userContext.setSex(user.getGender());
        return userContext;
    }

    /**
     * Create a new ReActAgent instance for the given userId.
     *
     * @return newly created ReActAgent
     */
    public ReActAgent createAgent(Toolkit toolkit, Memory memory, String sysPrompt, String sessionId, String userId, ToolExecutionContext context) {
        ReActAgent agent =
                ReActAgent.builder()
                        .name("supervisor_agent")
                        .sysPrompt(sysPrompt)
                        .toolkit(toolkit)
                        .hook(new StudioMessageHook(StudioManager.getClient()))
                        .toolExecutionContext(context)
                        .model(getModel())
                        .memory(memory)
                        .build();
        return agent;
    }

    public Model getModel() {
       return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .stream(true)
                .enableThinking(false)
                .formatter(new DashScopeChatFormatter())
                .build();
    }
}
