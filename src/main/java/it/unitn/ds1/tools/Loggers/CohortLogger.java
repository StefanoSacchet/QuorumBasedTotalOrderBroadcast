package it.unitn.ds1.tools.Loggers;

import it.unitn.ds1.tools.Loggers.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class CohortLogger extends Logger {
    public CohortLogger(String pathString) {
        super(pathString);
    }

    public void logUpdate(String replicaID, long epochNumber, long sequenceNumber, Integer value) {
        String logEntry = String.format("Replica %s update %d:%d %d%n", replicaID, epochNumber, sequenceNumber, value);

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
}
