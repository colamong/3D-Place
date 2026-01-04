package com.colombus.auth.infra.auth0.exception;

public class Auth0ServerException extends RuntimeException {
    public Auth0ServerException(String m) {
        super(m);
    }
}