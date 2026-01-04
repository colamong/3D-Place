package com.colombus.auth.infra.auth0.exception;

public class Auth0UnauthorizedException extends RuntimeException {
    public Auth0UnauthorizedException(String m) {
        super(m);
    }
}