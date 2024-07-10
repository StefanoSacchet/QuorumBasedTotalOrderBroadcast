package it.unitn.ds1.loggers;

import it.unitn.ds1.UpdateIdentifier;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class LogParser extends Logger {
    public final List<LogEntry> parsedList;

    public static class LogEntry {
        public LogType type;
        public String cohortID;
        public UpdateIdentifier updateIdentifier;
        public int value;
        public String clientID;

        public LogEntry(LogType type, String cohortID, UpdateIdentifier updateIdentifier, int value, String clientID) {
            this.cohortID = cohortID;
            this.updateIdentifier = updateIdentifier;
            this.value = value;
            this.clientID = clientID;
            this.type = type;
        }
        @Override
        public String toString() {
            return "LogEntry{" +
                    "type=" + type +
                    ", cohortID='" + cohortID + '\'' +
                    ", updateIdentifier=" + updateIdentifier +
                    ", value=" + value +
                    ", clientID='" + clientID + '\'' +
                    '}';
        }
    }

    public LogParser(String pathString) {
        super(pathString);
        this.parsedList = this.parseLogFile();
        System.out.println(this.parsedList);
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
                LogType type = null;
                if (parts[2].equals("update")) {
                    type = LogType.UPDATE;
                } else if (parts[2].equals("read") && parts[3].equals("req")) {
                    type = LogType.READ_REQ;
                } else if (parts[2].equals("read") && parts[3].equals("done")) {
                    type = LogType.READ_DONE;
                } else {
                    throw new Exception("Invalid log entry");
                }
                String clientID;
                int value;
                switch (type) {
                    case UPDATE:
                        String cohortID = parts[1];
                        UpdateIdentifier updateIdentifier = new UpdateIdentifier(Integer.parseInt(parts[3].split(":")[0]), Integer.parseInt(parts[3].split(":")[1]));
                        value = Integer.parseInt(parts[4]);
                        logEntries.add(new LogEntry(type, cohortID, updateIdentifier, value, null));
                        break;
                    case READ_REQ:
                        clientID = parts[1];
                        String replicaID = parts[5];
                        logEntries.add(new LogEntry(type, replicaID, null, -1, clientID));
                        break;
                    case READ_DONE:
                        clientID = parts[1];
                        value = Integer.parseInt(parts[4]);
                        logEntries.add(new LogEntry(type, null, null, value, clientID));
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
