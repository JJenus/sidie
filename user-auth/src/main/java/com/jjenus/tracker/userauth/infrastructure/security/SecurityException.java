package com.jjenus.tracker.userauth.infrastructure.security;

import com.jjenus.tracker.shared.exception.DomainException;

public class SecurityException extends DomainException {
    public SecurityException(String errorCode, String message) {
        super(errorCode, message);
    }
}
