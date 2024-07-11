package it.unitn.ds1.loggers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class ClientLogger extends Logger {
    public ClientLogger(String pathString) {
        super(pathString);
    }

    public void logRead(String clientID, int value) {
        String logEntry = String.format("Client %s read done %d%n", clientID, value);
        try {
            Files.write(this.path, logEntry.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }

    public void logReadReq(String clientID, String replicaID) {
        String logEntry = String.format("Client %s read req to %s%n", clientID, replicaID);
        try {
            Files.write(this.path, logEntry.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }

    public void logCrash(String clientID, String replicaID) {
        String logEntry = String.format("Client %s detected %s crashed%n", clientID, replicaID);
        try {
            Files.write(this.path, logEntry.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }
}
