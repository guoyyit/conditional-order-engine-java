package com.github.guoyyit.conditionalorderenginejava.engine;

/**
 * Thrown when a referenced order does not exist.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
