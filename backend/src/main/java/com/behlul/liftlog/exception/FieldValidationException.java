package com.behlul.liftlog.exception;

import java.util.Map;

/**
 * A request that passed bean validation but breaks a rule only the service can check (e.g. an exercise the
 * user cannot see). Answered like bean validation: 400 with fieldErrors.
 */
public class FieldValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public FieldValidationException(String field, String message) {
        super("Validation failed");
        this.fieldErrors = Map.of(field, message);
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
