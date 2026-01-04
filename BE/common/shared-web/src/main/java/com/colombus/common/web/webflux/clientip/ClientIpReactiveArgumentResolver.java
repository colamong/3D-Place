package com.colombus.common.web.webflux.clientip;

import com.colombus.common.web.core.clientip.CidrBlock;
import com.colombus.common.web.core.clientip.ClientIp;
import com.colombus.common.web.core.clientip.ClientIpUtil;
import com.colombus.common.web.core.clientip.TrustedProxyProperties;

import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ClientIpReactiveArgumentResolver implements HandlerMethodArgumentResolver {

    private final Set<CidrBlock> trusted;

    public ClientIpReactiveArgumentResolver(TrustedProxyProperties props) {
        var cidrs = Optional.ofNullable(props.trustedCidrs()).orElse(List.of());
        this.trusted = cidrs.stream().map(c -> {
            try { return CidrBlock.parse(c); } catch (Exception e) { return null; }
        }).filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean supportsParameter(@NonNull MethodParameter parameter) {
        return parameter.hasParameterAnnotation(ClientIp.class)
                && parameter.getParameterType() == String.class;
    }

    @Override
    public @NonNull Mono<Object> resolveArgument(@NonNull MethodParameter parameter, @NonNull BindingContext ctx, @NonNull ServerWebExchange ex) {
        // headers -> Map<String, List<String>>
        Map<String, List<String>> headers = new LinkedHashMap<>();
        ex.getRequest().getHeaders().forEach((k, v) -> headers.put(k, new ArrayList<>(v)));

        // remote address
        InetSocketAddress ra = ex.getRequest().getRemoteAddress();
        String remote = (ra != null && ra.getAddress() != null) ? ra.getAddress().getHostAddress() : null;

        String ip = ClientIpUtil.extractClientIp(headers, remote, trusted);
        return Mono.justOrEmpty(ip);
    }
}