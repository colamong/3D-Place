package com.colombus.auth.infra.auth0.exception;

public class Auth0ConflictException extends RuntimeException {
    public Auth0ConflictException(String m) {
        super(m);
    }
}