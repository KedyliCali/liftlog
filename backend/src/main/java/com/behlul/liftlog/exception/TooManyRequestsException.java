package com.behlul.liftlog.exception;

/** Answered with 429: the client (or everyone together) is asking faster than the server allows. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
