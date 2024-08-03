package it.unitn.ds1.tests.old_tests;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import it.unitn.ds1.classes.UpdateIdentifier;
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

public class TestCoordinatorCrashNoWriteOk {

    @BeforeAll
    static void setUp() throws InterruptedException {
        InUtils inUtils = new InUtils();
        List<ActorRef> clients = inUtils.clients;
        List<ActorRef> cohorts = inUtils.cohorts;
        ActorSystem system = inUtils.system;

        InUtils.threadSleep(1000);

        CommunicationWrapper.send(clients.get(2), new MessageCommand(MessageTypes.TEST_UPDATE));
        CommunicationWrapper.send(cohorts.get(0), new MessageCommand(MessageTypes.CRASH_NO_WRITEOK));

        InUtils.threadSleep(4000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int expected = 14; // N_COHORT - 1 (-1 is coordinator who crashed) + 1 for the write request + 4 for start election + 1 for leader found + 4 for leader found|election start + 4 for detected crash
        // + 4 for updates done
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        //check for read req and read done
        boolean updateRequestFound = false;
        int updateValue = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE_REQ && entry.firstActor.equals("client_2") && entry.secondActor.equals("cohort_2") && entry.value == 2000000) {
                updateRequestFound = true;
                updateValue = entry.value;
            }
        }
        assertTrue(updateRequestFound, "There should be an update request from client_2 to cohort_2 with value 2000000");
        assertEquals(2000000, updateValue, "The value of the update request should be 2000000");
        int detectedCrashes = 0;
        int startElectionCount = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.COHORT_DETECTS_COHORT_CRASH && MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.WRITEOK) {
                detectedCrashes++;
            } else if (entry.type == LogType.LEADER_ELECTION_START && entry.secondActor.equals("cohort_0")) {
                startElectionCount++;
            }
        }
        assertEquals(4, detectedCrashes, "There should be 4 detected crashes, because 4 replicas are alive");
        assertEquals(4, startElectionCount, "There should be 4 election starting, because 4 replicas are alive");

        boolean leaderFound = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.LEADER_FOUND) {
                leaderFound = true;
                break;
            }
        }
        assertTrue(leaderFound, "Leader should be found");

        int updatesFound = 0;
        UpdateIdentifier check = new UpdateIdentifier(1, 1);
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE && entry.value == 2000000 && entry.updateIdentifier.equals(check)) {
                updatesFound++;
            }
        }
        assertEquals(4, updatesFound, "There should be 4 updates done with value 2000000 and epoch 1 and sequence 1");
    }
}
