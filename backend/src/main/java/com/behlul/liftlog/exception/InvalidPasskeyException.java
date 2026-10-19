package com.behlul.liftlog.exception;

/**
 * A passkey request the server cannot accept: an unknown, expired or already used ceremony, a malformed
 * credential, or a registration that fails verification. Answered with 400; a sign-in whose signature does
 * not verify is an InvalidCredentialsException (401) instead.
 */
public class InvalidPasskeyException extends RuntimeException {

    public InvalidPasskeyException(String message) {
        super(message);
    }
}
