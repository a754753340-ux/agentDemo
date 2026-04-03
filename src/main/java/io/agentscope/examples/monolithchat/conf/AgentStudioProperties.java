package io.agentscope.examples.monolithchat.conf;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "agentscope.studio")
public class AgentStudioProperties {

    private boolean enabled = false;
    private String url = "";
    private String project = "monolith-chat";
    private String runNamePrefix = "stream-chat";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getRunNamePrefix() {
        return runNamePrefix;
    }

    public void setRunNamePrefix(String runNamePrefix) {
        this.runNamePrefix = runNamePrefix;
    }
}
