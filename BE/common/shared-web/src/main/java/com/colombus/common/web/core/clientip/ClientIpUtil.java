package com.colombus.common.web.core.clientip;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClientIpUtil {
    private ClientIpUtil() {}

    public static String extractClientIp(
            Map<String, List<String>> headers,
            String remoteAddress,
            Set<CidrBlock> trustedCidrs
    ) {
        String remote = ForwardedHeaders.stripPortAndBrackets(remoteAddress);
        if (remote == null || remote.isBlank()) return null;

        // 바로 앞 홉이 신뢰 프록시가 아니면 그것이 클라이언트
        if (!isTrusted(remote, trustedCidrs)) return remote;

        List<String> chain = new ArrayList<>();

        // RFC 7239 Forwarded: for=...
        String fwd = first(headers, "forwarded");
        if (fwd != null) chain.addAll(ForwardedHeaders.parseForwardedFor(fwd));

        // X-Forwarded-For: 가장 왼쪽이 원본
        String xff = first(headers, "x-forwarded-for");
        if (xff != null) chain.addAll(ForwardedHeaders.parseXForwardedFor(xff));

        if (chain.isEmpty()) {
            // 프록시인데 체인이 없으면 remote 반환(게이트웨이 설정 이슈 가정)
            return remote;
        }

        // 체인에서 "신뢰 프록시가 아닌" 첫 IP = 실제 클라이언트
        for (String ip : chain) {
            if (!isTrusted(ip, trustedCidrs)) return ip;
        }

        // 전부 신뢰 프록시였다면 가장 왼쪽(원본 추정)
        return chain.get(0);
    }

    private static String first(Map<String, List<String>> headers, String key) {
        if (headers == null) return null;
        for (var e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key) && !e.getValue().isEmpty()) {
                return e.getValue().get(0);
            }
        }
        return null;
    }

    private static boolean isTrusted(String ip, Set<CidrBlock> trusted) {
        if (ip == null) return false;
        try {
            InetAddress a = InetAddress.getByName(ip);
            for (CidrBlock c : trusted) if (c.contains(a)) return true;
            return false;
        } catch (Exception e) { return false; }
    }
}
