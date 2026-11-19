package com.freightfox.dispatch.exception;

/** Raised when a plan is requested before orders and/or vehicles were submitted. */
public class NoDataException extends RuntimeException {
    public NoDataException(String message) {
        super(message);
    }
}
