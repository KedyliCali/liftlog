package com.behlul.liftlog.dto;

import java.time.Instant;
import java.util.Map;

/** The single error shape every endpoint returns. */
public record ApiError(Instant timestamp, int status, String error, String message, String path,
                       Map<String, String> fieldErrors) {
}
