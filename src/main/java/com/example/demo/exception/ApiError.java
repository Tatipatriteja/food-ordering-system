package com.example.demo.exception;

import java.time.Instant;
import java.util.List;

/** The single JSON error shape returned by every failing API call. */
public record ApiError(int status, String error, String message, Instant timestamp, List<String> errors) {}
