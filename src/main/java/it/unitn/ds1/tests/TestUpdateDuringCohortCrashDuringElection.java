package it.unitn.ds1.tests;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import it.unitn.ds1.UpdateIdentifier;
import it.unitn.ds1.loggers.LogParser;
import it.unitn.ds1.loggers.LogType;
import it.unitn.ds1.messages.MessageCommand;
import it.unitn.ds1.messages.MessageTypes;
import it.unitn.ds1.tools.CommunicationWrapper;
import it.unitn.ds1.tools.DotenvLoader;
import it.unitn.ds1.tools.InUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestUpdateDuringCohortCrashDuringElection {
    @BeforeAll
    static void setUp() throws InterruptedException {
        InUtils testUtils = new InUtils();
        List<ActorRef> cohorts = testUtils.cohorts;
        List<ActorRef> clients = testUtils.clients;
        ActorSystem system = testUtils.system;

        InUtils.threadSleep(2000);
        CommunicationWrapper.send(cohorts.get(0), new MessageCommand(MessageTypes.CRASH));
        CommunicationWrapper.send(cohorts.get(2), new MessageCommand(MessageTypes.CRASH));

        CommunicationWrapper.send(cohorts.get(3), new MessageCommand(MessageTypes.TEST_UPDATE_DURING_ELECTION));
        InUtils.threadSleep(8000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int expected = 12; // 3 detect crash, 3 leader start, 1 update request, 1 update request received, 1 leader found, 3 updates done
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        int updateValue = 3000000;
        UpdateIdentifier checkUpdate = new UpdateIdentifier(1,1);

        int detectedCrashes = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.COHORT_DETECTS_COHORT_CRASH && logEntry.secondActor.equals("cohort_0")) {
                detectedCrashes++;
            }
        }
        assertEquals(3, detectedCrashes, "There should be 1 detected crash");


        int leaderElectionStarts = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.LEADER_ELECTION_START) {
                leaderElectionStarts++;
            }
        }
        assertEquals(3, leaderElectionStarts, "There should be 3 leader election starts");

        boolean updateRequestFound = false;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.UPDATE_REQ && logEntry.firstActor.equals("client_3") && logEntry.value == updateValue) {
                updateRequestFound = true;
            }
        }
        assertTrue(updateRequestFound, "There should be 1 update request");

        boolean updateRequestReceived = false;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.COHORT_RECEIVED_UPDATE_REQUEST_DURING_ELECTION && logEntry.firstActor.equals("cohort_3") && logEntry.secondActor.equals("client_3")) {
                updateRequestReceived = true;
                break;
            }
        }
        assertTrue(updateRequestReceived, "There should be 1 update request received");

        boolean leaderFound = false;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.LEADER_FOUND && logEntry.firstActor.equals("cohort_4")) {
                leaderFound = true;
                break;
            }
        }
        assertTrue(leaderFound, "There should be 1 leader found");

        int updatesDone = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.UPDATE && logEntry.updateIdentifier.equals(checkUpdate) && logEntry.value == updateValue) {
                updatesDone++;
            }
        }
        assertEquals(3, updatesDone, "There should be 3 updates done");
    }
}
