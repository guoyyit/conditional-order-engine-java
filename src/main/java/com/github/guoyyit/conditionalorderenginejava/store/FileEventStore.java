package com.github.guoyyit.conditionalorderenginejava.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.conditionalorderenginejava.config.EngineProperties;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * File backed append-only event store (one JSON object per line).
 */
@Component
public class FileEventStore implements EventStore {

    private final Path file;
    private final ObjectMapper objectMapper;
    private final boolean fsyncPerAppend;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicLong lastSeq = new AtomicLong();
    private RandomAccessFile appender;

    @Autowired
    public FileEventStore(EngineProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Path.of(properties.getDataDir()).resolve(properties.getEventLogFile()));
    }

    public FileEventStore(ObjectMapper objectMapper, Path file, boolean fsyncPerAppend) {
        this(engineProperties(file, fsyncPerAppend), objectMapper, file);
    }

    private FileEventStore(EngineProperties properties, ObjectMapper objectMapper, Path file) {
        this.objectMapper = objectMapper;
        this.fsyncPerAppend = properties.isFsyncPerAppend();
        this.file = file;
        Path dir = file.toAbsolutePath().getParent();
        try {
            Files.createDirectories(dir);
            if (Files.notExists(file)) {
                Files.createFile(file);
            }
            for (LoggedEvent event : readAllInternal()) {
                if (event.getSeq() > lastSeq.get()) {
                    lastSeq.set(event.getSeq());
                }
            }
            this.appender = new RandomAccessFile(file.toFile(), "rw");
            this.appender.seek(this.appender.length());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot initialize event log at " + file, e);
        }
    }

    private static EngineProperties engineProperties(Path file, boolean fsyncPerAppend) {
        EngineProperties properties = new EngineProperties();
        properties.setDataDir(file.toAbsolutePath().getParent().toString());
        properties.setEventLogFile(file.getFileName().toString());
        properties.setFsyncPerAppend(fsyncPerAppend);
        return properties;
    }

    @Override
    public LoggedEvent append(LoggedEvent event) {
        lock.lock();
        try {
            event.setSeq(lastSeq.incrementAndGet());
            if (event.getTimestamp() <= 0) {
                event.setTimestamp(System.currentTimeMillis());
            }
            byte[] line = objectMapper.writeValueAsBytes(event);
            appender.write(line);
            appender.write('\n');
            if (fsyncPerAppend) {
                appender.getFD().sync();
            }
            return event;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot append event to " + file, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<LoggedEvent> readAll() {
        return readAllInternal();
    }

    private List<LoggedEvent> readAllInternal() {
        List<LoggedEvent> events = new ArrayList<>();
        if (Files.notExists(file)) {
            return events;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    events.add(objectMapper.readValue(line, LoggedEvent.class));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read event log " + file, e);
        }
        events.sort((a, b) -> Long.compare(a.getSeq(), b.getSeq()));
        return events;
    }
}
