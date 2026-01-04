package com.colombus.auth.infra.auth0.exception;

public class Auth0NotFoundException extends RuntimeException {
    public Auth0NotFoundException(String m) {
        super(m);
    }
}