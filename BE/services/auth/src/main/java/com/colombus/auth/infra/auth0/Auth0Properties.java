package com.colombus.auth.infra.auth0;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth0")
public record Auth0Properties(
  String domain,
  String appClientId,
  String dbConnection,
  Mgmt mgmt
) {
  public record Mgmt(String clientId, String clientSecret, String audience) {}
}