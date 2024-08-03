package it.unitn.ds1.tests.arbitrary_cohorts_num;

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
import it.unitn.ds1.tools.TestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestUpdateRequestCrashCoordinator {

    private static DotenvLoader dotenv;
    private static ActorRef crashedCohort;
    private static ActorRef clientRead;
    private static ActorRef clientUpdate;
    private static String cohortRead;
    private static String cohortUpdate;
    private static int updateVal;

    @BeforeAll
    static void setUp() throws InterruptedException {
        dotenv = DotenvLoader.getInstance();

        InUtils inUtils = new InUtils();
        List<ActorRef> cohorts = inUtils.cohorts;
        List<ActorRef> clients = inUtils.clients;
        ActorSystem system = inUtils.system;

        InUtils.threadSleep(1000);

        clientRead = clients.get(2);
        cohortRead = TestUtils.getCohortFromClient(clientRead);
        CommunicationWrapper.send(clients.get(2), new MessageCommand(MessageTypes.TEST_READ));

        crashedCohort = cohorts.get(0);
        CommunicationWrapper.send(crashedCohort, new MessageCommand(MessageTypes.CRASH));

        clientUpdate = clients.get(2);
        cohortUpdate = TestUtils.getCohortFromClient(clientUpdate);
        updateVal = TestUtils.getUpdateValueFromClient(clientUpdate);
        CommunicationWrapper.send(clients.get(2), new MessageCommand(MessageTypes.TEST_UPDATE));

        InUtils.threadSleep(9000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int N_COHORTS = dotenv.getNCohorts();
        int expected = 2 * (N_COHORTS - 1) + 1 + 1 + 1 + 1 + 1; // 1 read req + 1 read done + 1 update req + 1 crash detect + (N_COHORTS - 1) election start + 1 leader found + (N_COHORTS - 1) update done
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        // check for read req and read done
        boolean readRequestFound = false;
        boolean readDoneFound = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.READ_REQ && entry.firstActor.equals(clientRead.path().name()) && entry.secondActor.equals(cohortRead)) {
                readRequestFound = true;
            } else if (entry.type == LogType.READ_DONE && entry.firstActor.equals(clientRead.path().name()) && entry.value == 0) {
                readDoneFound = true;
            }
        }
        assertTrue(readRequestFound, "Read request should be found");
        assertTrue(readDoneFound, "Read done should be found");

        // check crash detect
        int detectedCrashes = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.COHORT_DETECTS_COHORT_CRASH && MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.UPDATE && entry.secondActor.equals(crashedCohort.path().name())) {
                detectedCrashes++;
            }
        }
        assertEquals(1, detectedCrashes, "There should be " + (N_COHORTS - 1) + " detected crashes");

        // check for update and crash
        boolean updateRequestFound = false;
        boolean crashDetected = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE_REQ && entry.firstActor.equals(clientUpdate.path().name()) && entry.secondActor.equals(cohortUpdate) && entry.value == updateVal) {
                updateRequestFound = true;
            } else if (entry.type == LogType.LEADER_ELECTION_START && entry.secondActor.equals(crashedCohort.path().name())) {
                crashDetected = true;
            }
        }
        assertTrue(updateRequestFound, "Update request should be found");
        assertTrue(crashDetected, "Coordinator crash due to no response to update should be found");

        // check leader found
        boolean leaderFound = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.LEADER_FOUND) {
                leaderFound = true;
                break;
            }
        }
        assertTrue(leaderFound, "Leader should be found");

        // check for updates done
        int updatesFound = 0;
        UpdateIdentifier check = new UpdateIdentifier(1, 1);
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE && entry.value == updateVal && entry.updateIdentifier.equals(check)) {
                updatesFound++;
            }
        }
        assertEquals(N_COHORTS - 1, updatesFound, "There should be " + (N_COHORTS - 1) + " updates done with value " + updateVal);
    }
}
