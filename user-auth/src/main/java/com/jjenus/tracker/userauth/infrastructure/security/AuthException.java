package com.jjenus.tracker.userauth.infrastructure.security;

import com.jjenus.tracker.shared.exception.DomainException;

public class AuthException extends DomainException {
    public AuthException(String errorCode, String message) {
        super(errorCode, message);
    }
}
