package it.unitn.ds1.loggers;

import it.unitn.ds1.UpdateIdentifier;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class LogParser extends Logger {
    public final List<LogEntry> parsedList;

    public static class LogEntry {
        public LogType type;
        public String firstActor;
        public String secondActor;
        public UpdateIdentifier updateIdentifier;
        public Integer value;
        public Integer oldState;
        public String causeOfCrash;

        public LogEntry(LogType type, String firstActor, String secondActor, UpdateIdentifier updateIdentifier, Integer value, Integer oldState, String causeOfCrash) {
            this.firstActor = firstActor;
            this.secondActor = secondActor;
            this.updateIdentifier = updateIdentifier;
            this.value = value;
            this.oldState = oldState;
            this.type = type;
            this.causeOfCrash = causeOfCrash;
        }

        @Override
        public String toString() {
            if (this.type == LogType.CLIENT_DETECTS_COHORT_CRASH || this.type == LogType.COHORT_DETECTS_COHORT_CRASH) {
                return "LogEntry{" +
                        "type=" + type +
                        ", detector='" + firstActor + '\'' +
                        ", crashed='" + secondActor + '\'' +
                        ", causeOfCrash=" + causeOfCrash +
                        '}';
            } else if (this.type == LogType.FLUSH) {
                return "LogEntry{" +
                        "type=" + type +
                        ", replicaID='" + firstActor + '\'' +
                        ", oldState=" + oldState +
                        ", newState=" + value +
                        '}';
            }
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
    }

    private LogType parseType(String[] parts) {
        LogType type = null;
        switch (parts[2]) {
            case "update":
                if (parts[0].equals("Client")) {
                    type = LogType.UPDATE_REQ;
                } else {
                    type = LogType.UPDATE;
                }
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
            case "started":
                type = LogType.LEADER_ELECTION_START;
                break;
            case "leader":
                type = LogType.LEADER_FOUND;
                break;
            case "flush":
                type = LogType.FLUSH;
                break;
            case "received":
                if (parts[3].equals("update")) {
                    type = LogType.COHORT_RECEIVED_UPDATE_REQUEST_DURING_ELECTION;
                } else if (parts[3].equals("read")) {
                    type = LogType.COHORT_RECEIVED_READ_REQUEST_DURING_ELECTION;
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
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split(" ");
                LogType type = parseType(parts);

                String clientID;
                String replicaID;
                String causeOfCrash;
                String crashedActorID;
                int value;
                switch (type) {
                    case UPDATE:
                        String cohortID = parts[1];
                        UpdateIdentifier updateIdentifier = new UpdateIdentifier(Integer.parseInt(parts[3].split(":")[0]), Integer.parseInt(parts[3].split(":")[1]));
                        value = Integer.parseInt(parts[4]);
                        logEntries.add(new LogEntry(type, cohortID, null, updateIdentifier, value, -1, null));
                        break;
                    case READ_REQ:
                        clientID = parts[1];
                        replicaID = parts[5];
                        logEntries.add(new LogEntry(type, clientID, replicaID, null, null, null, null));
                        break;
                    case READ_DONE:
                        clientID = parts[1];
                        value = Integer.parseInt(parts[4]);
                        logEntries.add(new LogEntry(type, clientID, null, null, value, null, null));
                        break;
                    case CLIENT_DETECTS_COHORT_CRASH:
                        clientID = parts[1];
                        replicaID = parts[3];
                        logEntries.add(new LogEntry(type, clientID, replicaID, null, null, null, null));
                        break;
                    case COHORT_DETECTS_COHORT_CRASH:
                        String detector = parts[1];
                        crashedActorID = parts[3];
                        causeOfCrash = parts[8];
                        logEntries.add(new LogEntry(type, detector, crashedActorID, null, null, null, causeOfCrash));
                        break;
                    case UPDATE_REQ:
                        clientID = parts[1];
                        replicaID = parts[5];
                        value = Integer.parseInt(parts[8]);
                        logEntries.add(new LogEntry(type, clientID, replicaID, null, value, null, null));
                        break;
                    case LEADER_ELECTION_START:
                        replicaID = parts[1];
                        crashedActorID = parts[5];
                        logEntries.add(new LogEntry(type, replicaID, crashedActorID, null, null, null, null));
                        break;
                    case LEADER_FOUND:
                        String leaderID = parts[1];
                        logEntries.add(new LogEntry(type, leaderID, null, null, null, null, null));
                        break;
                    case FLUSH:
                        replicaID = parts[1];
                        int oldState = Integer.parseInt(parts[4]);
                        int newState = Integer.parseInt(parts[6]);
                        logEntries.add(new LogEntry(type, replicaID, null, null, newState, oldState, null));
                        break;
                    case COHORT_RECEIVED_UPDATE_REQUEST_DURING_ELECTION:
                        replicaID = parts[1];
                        clientID = parts[5];
                        value = Integer.parseInt(parts[11]);
                        logEntries.add(new LogEntry(type, replicaID, clientID, null, value, null, null));
                        break;
                    case COHORT_RECEIVED_READ_REQUEST_DURING_ELECTION:
                        replicaID = parts[1];
                        clientID = parts[5];
                        logEntries.add(new LogEntry(type, replicaID, clientID, null, null, null, null));
                        break;
                    default:
                        throw new Exception("Invalid log entry" + type);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return logEntries;
    }
}
