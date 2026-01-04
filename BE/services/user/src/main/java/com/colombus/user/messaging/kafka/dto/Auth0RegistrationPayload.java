package com.colombus.user.messaging.kafka.dto;

public record Auth0RegistrationPayload(
    String trigger,
    String user_id,
    String email,
    Boolean email_verified,
    String provider,
    String tenant,
    String connection,
    String created_at,
    String client_id,
    String app_name,
    String locale,
    String avatarUrl
) {}