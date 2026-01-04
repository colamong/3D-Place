package com.colombus.auth.security.support;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import com.colombus.user.contract.enums.AuthProviderCode;
import jakarta.annotation.Nullable;

public final class IdpResolver {

    public record IdpTriple(AuthProviderCode provider, String tenant, String sub) {}

    private IdpResolver() {}

    public static IdpTriple resolveIdp(@Nullable Authentication auth, @Nullable OidcUser user, @Nullable String issuerUri) {
        if (auth != null && auth.getDetails() instanceof Map<?, ?> details) {
            Object idpObj = details.get("idp");
            if (idpObj instanceof Map<?, ?> idp) {
                AuthProviderCode provider = null;
                Object pv = idp.get("provider");
                if (pv instanceof AuthProviderCode p1) provider = p1;
                else if (pv != null) provider = mapProvider(pv.toString());

                String tenant = Optional.ofNullable(idp.get("tenant"))
                        .map(Object::toString).filter(s -> !s.isBlank()).orElse(null);
                String sub = Optional.ofNullable(idp.get("sub"))
                        .map(Object::toString).filter(s -> !s.isBlank()).orElse(null);

                if (provider != null && sub != null) {
                    return new IdpTriple(provider, tenantOrFallback(tenant, issuerUri, user), sub);
                }
            }
        }

        AuthProviderCode provider = null;
        if (auth instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken oat) {
            provider = mapProvider(oat.getAuthorizedClientRegistrationId());
        }

        String sub = (user != null ? user.getSubject() : null);
        String tenant = tenantOrFallback(null, issuerUri, user);

        if (provider == null) provider = AuthProviderCode.AUTH0;
        if (sub == null) sub = "unknown-sub";

        return new IdpTriple(provider, tenant, sub);
    }

    private static String tenantOrFallback(@Nullable String tenant, @Nullable String issuerUri, @Nullable OidcUser user) {
        if (tenant != null && !tenant.isBlank()) return tenant;
        String iss = issuerUri;
        if ((iss == null || iss.isBlank()) && user != null && user.getIssuer() != null) {
            iss = user.getIssuer().toString();
        }
        if (iss != null && !iss.isBlank()) {
            try {
                String host = URI.create(iss).getHost();
                if (host != null && !host.isBlank()) return host;
            } catch (Exception ignored) {}
        }
        return "unknown";
    }

    private static AuthProviderCode mapProvider(@Nullable String id) {
        if (id == null) return AuthProviderCode.AUTH0;
        String k = id.trim().toLowerCase();
        return switch (k) {
            case "auth0" -> AuthProviderCode.AUTH0;
            case "google", "google-oidc", "google_oauth2" -> AuthProviderCode.GOOGLE;
            // case "github" -> AuthProviderCode.GITHUB;
            // case "azure", "azuread", "microsoft", "microsoft-azure-ad" -> AuthProviderCode.AZUREAD;
            default -> AuthProviderCode.AUTH0;
        };
    }
}