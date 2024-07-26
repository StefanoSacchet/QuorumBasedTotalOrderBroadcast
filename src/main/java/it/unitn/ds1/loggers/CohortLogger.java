package it.unitn.ds1.loggers;

import it.unitn.ds1.messages.MessageTypes;

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

    public void logCrash(String detectorID, String crashedID, MessageTypes cause) {
        String logEntry = String.format("Cohort %s detected %s crashed due to no %s%n", detectorID, crashedID, cause);
        try {
            Files.write(this.path, logEntry.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }

    public void logLeaderElectionStart(String firstActor, String secondActor) {
        String logEntry = String.format("Cohort %s started leader election %s crashed%n", firstActor, secondActor);
        try {
            Files.write(this.path, logEntry.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }

    public void logLeaderFound(String leaderID) {
        String logEntry = String.format("Cohort %s leader found %n", leaderID);
        try {
            Files.write(this.path, logEntry.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }

    public void logFlush(String replicaID, int oldState, int newState) {
        String logEntry = String.format("Replica %s flush state %s -> %s%n", replicaID, oldState, newState);
        try {
            Files.write(this.path, logEntry.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }

    public void logReadRequestDuringElection(String firstActor, String secondActor){
        String logEntry = String.format("Replica %s received read request during election from %s%n", firstActor, secondActor);
        try {
            Files.write(this.path, logEntry.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }
    public void logUpdateRequestDuringElection(String firstActor, String secondActor, int newState){
        String logEntry = String.format("Replica %s received update request during election from %s with state %s%n", firstActor, secondActor, newState);
        try {
            Files.write(this.path, logEntry.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }
}
