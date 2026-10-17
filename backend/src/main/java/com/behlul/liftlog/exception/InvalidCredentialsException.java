package com.behlul.liftlog.exception;

/** Same message for unknown email and wrong password, so the API never reveals which accounts exist. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
