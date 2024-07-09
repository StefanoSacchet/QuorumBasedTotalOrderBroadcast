package it.unitn.ds1.tools;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.io.IOException;
import java.nio.file.Path;

public class Logger {

    String path;

    public Logger(String path) {
        this.path = path;
    }

    public static void clearFile(String path) {
        Path tmp = Paths.get(path);
        if (Files.exists(tmp)) {
            try {
                Files.delete(tmp);
            } catch (IOException e) {
                System.err.println("Error deleting log file: " + e.getMessage());
            }
        }
    }

    public void appendToLogFile(String replicaID, long epochNumber, long sequenceNumber, Integer value) {
        String logEntry = String.format("Replica %s update %d:%d %d%n", replicaID, epochNumber, sequenceNumber, value);

        try {
            Path path = Paths.get(this.path);
            Path dir = Paths.get(this.path.split("/")[1]);

            // If file doesn't exist, create it
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            // Append the log entry to the file
            Files.write(path, logEntry.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }
}
