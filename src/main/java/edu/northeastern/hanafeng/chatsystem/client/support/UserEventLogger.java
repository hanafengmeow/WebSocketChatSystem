package edu.northeastern.hanafeng.chatsystem.client.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class UserEventLogger {

    @Value("${client.logs.directory:./logs}")
    private String logsDirectory;

    private final BlockingQueue<UserEvent> eventQueue = new LinkedBlockingQueue<>(10000);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread writerThread;
    private BufferedWriter writer;
    private String logFilePath;

    public void initialize() {
        try {
            Path logsPath = Paths.get(logsDirectory);
            Files.createDirectories(logsPath);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            logFilePath = logsDirectory + "/user_events_" + timestamp + ".csv";

            writer = new BufferedWriter(new FileWriter(logFilePath));
            writer.write("userId,roomId,eventType,messageId,timestamp,sequenceNumber\n");
            writer.flush();

            writerThread = new Thread(this::processEvents);
            writerThread.setName("user-event-writer");
            writerThread.setDaemon(false);
            writerThread.start();

            log.info("✅ UserEventLogger initialized: {}", logFilePath);
        } catch (IOException e) {
            log.error("Failed to initialize UserEventLogger", e);
        }
    }

    public void logEvent(int userId, int roomId, String eventType, String messageId, int sequenceNumber) {
        try {
            UserEvent event = new UserEvent(userId, roomId, eventType, messageId, 
                                           System.currentTimeMillis(), sequenceNumber);
            eventQueue.offer(event);
        } catch (Exception e) {
            log.error("Failed to queue user event", e);
        }
    }

    private void processEvents() {
        while (running.get() || !eventQueue.isEmpty()) {
            try {
                UserEvent event = eventQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (event != null) {
                    writeEvent(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void writeEvent(UserEvent event) {
        try {
            writer.write(String.format("%d,%d,%s,%s,%d,%d\n",
                event.userId, event.roomId, event.eventType, event.messageId,
                event.timestamp, event.sequenceNumber));
            writer.flush();
        } catch (IOException e) {
            log.error("Failed to write user event", e);
        }
    }

    public void shutdown() {
        try {
            log.info("Shutting down UserEventLogger...");
            running.set(false);

            if (writerThread != null) {
                writerThread.join(5000);
            }

            if (writer != null) {
                writer.close();
            }

            long eventCount = Files.lines(Paths.get(logFilePath)).count() - 1;
            log.info("✅ UserEventLogger closed. Events logged: {}, File: {}", eventCount, logFilePath);
        } catch (Exception e) {
            log.error("Error during UserEventLogger shutdown", e);
        }
    }

    private record UserEvent(int userId, int roomId, String eventType, 
                            String messageId, long timestamp, int sequenceNumber) {}
}