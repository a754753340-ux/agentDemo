package io.agentscope.examples.monolithchat.tools;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class DataTool implements AgentTool {


    @Override
    public String getName() {
        log.info("DataTool.getName()");
        return "";
    }

    @Override
    public String getDescription() {
        log.info("DataTool.getDescription()");
        return "";
    }

    @Override
    public Map<String, Object> getParameters() {
        log.info("DataTool.getParameters()");
        return Map.of();
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        log.info("DataTool.callAsync()");
        return null;
    }
}
