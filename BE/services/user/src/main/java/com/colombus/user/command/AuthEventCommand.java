package com.colombus.user.command;

import com.colombus.user.model.type.AuthEventKind;

public record AuthEventCommand(
    String provider,
    String detail,
    AuthEventKind kind,
    String ipAddr,
    String userAgent
) {}