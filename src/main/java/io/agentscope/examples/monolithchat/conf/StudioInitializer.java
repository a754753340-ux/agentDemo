package io.agentscope.examples.monolithchat.conf;

import io.agentscope.core.studio.StudioManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StudioInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(StudioInitializer.class);

    private final AgentStudioProperties properties;

    public StudioInitializer(AgentStudioProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            logger.info("AgentScope Studio is disabled");
            return;
        }
        String runNamePrefix = properties.getRunNamePrefix() == null || properties.getRunNamePrefix().isBlank()
                ? "stream-chat"
                : properties.getRunNamePrefix();
        String runName = runNamePrefix + "_" + System.currentTimeMillis();

        try {
            StudioManager.init()
                    .studioUrl(properties.getUrl())
                    .project(properties.getProject())
                    .runName(runName)
                    .initialize()
                    .block();
            logger.info("AgentScope Studio initialized: url={}, project={}", properties.getUrl(), properties.getProject());
        } catch (Exception e) {
            logger.error("Error initializing AgentScope Studio", e);
        }
    }
}
