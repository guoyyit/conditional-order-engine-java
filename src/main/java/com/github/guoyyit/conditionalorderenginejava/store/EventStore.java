package com.github.guoyyit.conditionalorderenginejava.store;

import java.util.List;

/**
 * Durable append-only event log used for recovery and auditing.
 */
public interface EventStore {

    /**
     * Appends an event, assigning its sequence number, fsyncing according to
     * configuration, and returning the stored copy.
     */
    LoggedEvent append(LoggedEvent event);

    /**
     * Reads all events in sequence order (used at startup for recovery).
     */
    List<LoggedEvent> readAll();
}
