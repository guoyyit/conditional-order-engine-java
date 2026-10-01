package com.github.guoyyit.conditionalorderenginejava.engine;

/**
 * Thrown on a deterministic conflict: an operation lost against a concurrent
 * trigger, modification or cancellation, or optimistic version mismatch.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
