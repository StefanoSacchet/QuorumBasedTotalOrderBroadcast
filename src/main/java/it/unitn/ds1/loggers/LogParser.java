package it.unitn.ds1.loggers;

import it.unitn.ds1.UpdateIdentifier;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LogParser extends Logger {
    public final List<LogEntry> parsedList;

    public static class LogEntry {
        public LogType type;
        public String firstActor;
        public String secondActor;
        public UpdateIdentifier updateIdentifier;
        public int value;

        public LogEntry(LogType type, String firstActor, String secondActor, UpdateIdentifier updateIdentifier, int value) {
            this.firstActor = firstActor;
            this.secondActor = secondActor;
            this.updateIdentifier = updateIdentifier;
            this.value = value;
            this.type = type;
        }

        @Override
        public String toString() {
            if (this.type == LogType.CLIENT_DETECTS_COHORT_CRASH || this.type == LogType.COHORT_DETECTS_COHORT_CRASH)
                return "LogEntry{" +
                        "type=" + type +
                        ", detector='" + firstActor + '\'' +
                        ", crashed='" + secondActor + '\'' +
                        '}';

            return "LogEntry{" +
                    "type=" + type +
                    ", cohortID='" + firstActor + '\'' +
                    ", updateIdentifier=" + updateIdentifier +
                    ", value=" + value +
                    ", clientID='" + secondActor + '\'' +
                    '}';
        }
    }

    public LogParser(String pathString) {
        super(pathString);
        this.parsedList = this.parseLogFile();
        System.out.println(this.parsedList);
    }

    private LogType parseType(String[] parts) {
        LogType type = null;
        switch (parts[2]) {
            case "update":
                type = LogType.UPDATE;
                break;
            case "read":
                if (parts[3].equals("done")) {
                    type = LogType.READ_DONE;
                } else if (parts[3].equals("req")) {
                    type = LogType.READ_REQ;
                }
                break;
            case "detected":
                if (parts[0].equals("Client")) {
                    type = LogType.CLIENT_DETECTS_COHORT_CRASH;
                } else if (parts[0].equals("Cohort")) {
                    type = LogType.COHORT_DETECTS_COHORT_CRASH;
                }
                break;
            default:
                throw new RuntimeException("New log type found: " + parts[2]);
        }

        return type;
    }

    public List<LogEntry> parseLogFile() {
        List<LogEntry> logEntries = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(this.path);
            for (String line : lines) {
                System.out.println(line);
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split(" ");
                LogType type = parseType(parts);

                String clientID;
                String replicaID;
                int value;
                switch (type) {
                    case UPDATE:
                        String cohortID = parts[1];
                        UpdateIdentifier updateIdentifier = new UpdateIdentifier(Integer.parseInt(parts[3].split(":")[0]), Integer.parseInt(parts[3].split(":")[1]));
                        value = Integer.parseInt(parts[4]);
                        logEntries.add(new LogEntry(type, cohortID, null, updateIdentifier, value));
                        break;
                    case READ_REQ:
                        clientID = parts[1];
                        replicaID = parts[5];
                        logEntries.add(new LogEntry(type, clientID, replicaID, null, -1));
                        break;
                    case READ_DONE:
                        clientID = parts[1];
                        value = Integer.parseInt(parts[4]);
                        logEntries.add(new LogEntry(type, clientID, null, null, value));
                        break;
                    case CLIENT_DETECTS_COHORT_CRASH:
                        clientID = parts[1];
                        replicaID = parts[3];
                        logEntries.add(new LogEntry(type, clientID, replicaID, null, -1));
                        break;
                    case COHORT_DETECTS_COHORT_CRASH:
                        String detector = parts[1];
                        String crashed = parts[3];
                        logEntries.add(new LogEntry(type, detector, crashed, null, -1));
                        break;
                    default:
                        throw new Exception("Invalid log entry");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return logEntries;
    }
}
