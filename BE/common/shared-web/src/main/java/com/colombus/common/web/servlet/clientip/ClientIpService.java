package com.colombus.common.web.servlet.clientip;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.colombus.common.web.core.clientip.ClientIpCoreSupport;
import com.colombus.common.web.core.clientip.TrustedProxyProperties;

public class ClientIpService {

    private final TrustedProxyProperties props;

    public ClientIpService(TrustedProxyProperties props) {
        this.props = props;
    }

    public String resolve(HttpServletRequest req) {
        Map<String, List<String>> headers = new HashMap<>();
        var names = req.getHeaderNames();
        while (names.hasMoreElements()) {
            String h = names.nextElement();
            headers.put(h, Collections.list(req.getHeaders(h)));
        }
        return ClientIpCoreSupport.extract(headers, req.getRemoteAddr(), props);
    }
}