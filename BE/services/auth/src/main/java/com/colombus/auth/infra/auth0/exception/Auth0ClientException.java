package com.colombus.auth.infra.auth0.exception;

public class Auth0ClientException extends RuntimeException {
    public Auth0ClientException(String m) {
        super(m);
    }
}