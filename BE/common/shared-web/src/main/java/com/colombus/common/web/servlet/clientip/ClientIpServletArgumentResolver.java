package com.colombus.common.web.servlet.clientip;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.colombus.common.web.core.clientip.ClientIp;

public class ClientIpServletArgumentResolver implements HandlerMethodArgumentResolver {

    private final ClientIpService ipService;

    public ClientIpServletArgumentResolver(ClientIpService ipService) { this.ipService = ipService; }

    @Override
    public boolean supportsParameter(@NonNull MethodParameter parameter) {
        return parameter.getParameterType() == String.class
            && parameter.hasParameterAnnotation(ClientIp.class);
    }

    @Override
    public Object resolveArgument(
        @NonNull MethodParameter parameter,
        @Nullable ModelAndViewContainer mavContainer,
        @NonNull NativeWebRequest webRequest,
        @Nullable WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest req = webRequest.getNativeRequest(HttpServletRequest.class);
        return ipService.resolve(req);
    }
}