package com.github.guoyyit.conditionalorderenginejava.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.conditionalorderenginejava.config.EngineProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.FileOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 JSON Lines 文件的事件日志实现，追加写并立即刷盘，保证崩溃可恢复。
 * 崩溃时最后一行可能不完整，加载时跳过无法解析的尾部行。
 */
@Component
public class FileEventLogStore implements EventLogStore {

    private static final String LOG_FILE_NAME = "conditional-orders.log";

    private final Path logFile;
    private final ObjectMapper mapper = new ObjectMapper();
    private final java.io.OutputStream stream;

    @Autowired
    public FileEventLogStore(EngineProperties properties) {
        this(Path.of(properties.getStoreDir()));
    }

    public FileEventLogStore(Path storeDir) {
        try {
            Files.createDirectories(storeDir);
            this.logFile = storeDir.resolve(LOG_FILE_NAME);
            this.stream = new FileOutputStream(logFile.toFile(), true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void append(OrderEvent event) {
        try {
            stream.write(mapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8));
            stream.write('\n');
            stream.flush();
            if (stream instanceof FileOutputStream fos) {
                fos.getChannel().force(true);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<OrderEvent> loadAll() {
        if (!Files.exists(logFile)) {
            return List.of();
        }
        List<OrderEvent> events = new ArrayList<>();
        try (var lines = Files.lines(logFile, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                if (line.isBlank()) {
                    return;
                }
                try {
                    events.add(mapper.readValue(line, OrderEvent.class));
                } catch (Exception ignored) {
                    // 崩溃导致的不完整尾部行，跳过
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return events;
    }

    @Override
    public synchronized void close() {
        try {
            stream.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
