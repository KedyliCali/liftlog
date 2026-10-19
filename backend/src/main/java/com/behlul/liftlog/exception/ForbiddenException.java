package com.behlul.liftlog.exception;

/**
 * The caller is signed in but may not do this (403), for example a group member trying an owner action. A
 * project exception rather than Spring Security's AccessDeniedException, so services stay free of Spring Security.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
