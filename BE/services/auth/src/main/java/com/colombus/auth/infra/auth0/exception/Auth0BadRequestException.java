package com.colombus.auth.infra.auth0.exception;

public class Auth0BadRequestException extends RuntimeException {
    public Auth0BadRequestException(String m) {
        super(m);
    }
}