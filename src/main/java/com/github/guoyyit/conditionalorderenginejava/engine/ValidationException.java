package com.github.guoyyit.conditionalorderenginejava.engine;

/**
 * Thrown when a request fails semantic validation.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
