package com.colombus.common.web.core.clientip;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "net.client-ip")
public record TrustedProxyProperties(
    List<String> trustedCidrs,
    int maxTrustedHops
) {
    public TrustedProxyProperties {
        if (trustedCidrs == null) {
            trustedCidrs = List.of();
        }
    }
}