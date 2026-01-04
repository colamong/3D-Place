package com.colombus.auth.web.internal.dto;


public record LinkIdentityRequest (
    String primaryAuth0UserId,
    String secondaryIdToken      
) {}