package com.colombus.common.web.core.clientip;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


public class ClientIpCoreSupport {
    private ClientIpCoreSupport() {}

    /**
     * 헤더/remoteAddr/신뢰 프록시 설정 기반으로 실제 클라이언트 IP 추출
     */
    public static String extract(
        Map<String, List<String>> headers,
        String remoteAddr,
        TrustedProxyProperties props
    ) {
        Set<CidrBlock> trusted = new LinkedHashSet<>();
        for (String c : Optional.ofNullable(props.trustedCidrs()).orElse(List.of())) {
            try { trusted.add(CidrBlock.parse(c)); } catch (Exception ignore) {}
        }
        return ClientIpUtil.extractClientIp(headers, remoteAddr, trusted);
    }
}