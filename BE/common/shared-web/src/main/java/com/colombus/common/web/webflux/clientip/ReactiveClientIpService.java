package com.colombus.common.web.webflux.clientip;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.server.ServerWebExchange;

import com.colombus.common.web.core.clientip.ClientIpCoreSupport;
import com.colombus.common.web.core.clientip.TrustedProxyProperties;

import reactor.core.publisher.Mono;

public class ReactiveClientIpService {

    private final TrustedProxyProperties props;

    public ReactiveClientIpService(TrustedProxyProperties props) {
        this.props = props;
    }

    public Mono<String> resolve(ServerWebExchange ex) {
        return Mono.fromSupplier(() -> {
            Map<String, List<String>> headers = new LinkedHashMap<>();
            ex.getRequest().getHeaders().forEach((k, v) -> headers.put(k, List.copyOf(v)));

            var ra = ex.getRequest().getRemoteAddress();
            String remote = (ra != null && ra.getAddress() != null)
                ? ra.getAddress().getHostAddress()
                : "unknown";

            return ClientIpCoreSupport.extract(headers, remote, props);
        });
    }
}