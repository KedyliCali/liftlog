package com.behlul.liftlog.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " " + id + " not found");
    }

    public ResourceNotFoundException(String resource, String id) {
        super(resource + " " + id + " not found");
    }
}
