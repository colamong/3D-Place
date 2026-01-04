package com.colombus.auth.application.link;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.link")
public class LinkProperties {
    private List<String> allowedConnections = new ArrayList<>();

    public List<String> getAllowedConnections() { return allowedConnections; }
    public void setAllowedConnections(List<String> allowedConnections) { this.allowedConnections = allowedConnections; }
}
