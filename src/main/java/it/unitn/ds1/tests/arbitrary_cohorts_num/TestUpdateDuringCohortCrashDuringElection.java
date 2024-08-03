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

public class TestUpdateDuringCohortCrashDuringElection {

    private static DotenvLoader dotenv;
    private static ActorRef clientUpdate;
    private static String cohortUpdate;
    private static int updateVal;
    private static ActorRef firstCrash;
    private static ActorRef secondCrash;
    private static ActorRef newLeader;

    private static int prevHeartbeat;
    private static int prevHeartbeatTimeout;

    @BeforeAll
    static void setUp() throws InterruptedException {
        dotenv = DotenvLoader.getInstance();

        prevHeartbeat = dotenv.getHeartbeat();
        prevHeartbeatTimeout = dotenv.getHeartbeatTimeout();
        dotenv.setHeartbeat(4000);
        dotenv.setHeartbeatTimeout(dotenv.getHeartbeat() + 500);

        InUtils testUtils = new InUtils();
        List<ActorRef> cohorts = testUtils.cohorts;
        List<ActorRef> clients = testUtils.clients;
        ActorSystem system = testUtils.system;

        InUtils.threadSleep(1000);

        firstCrash = cohorts.get(0);
        CommunicationWrapper.send(firstCrash, new MessageCommand(MessageTypes.CRASH));
        secondCrash = cohorts.get(2);
        CommunicationWrapper.send(secondCrash, new MessageCommand(MessageTypes.CRASH));

        newLeader = cohorts.get(cohorts.size() - 1);

        clientUpdate = clients.get(3);
        cohortUpdate = TestUtils.getCohortFromClient(clientUpdate);
        updateVal = TestUtils.getUpdateValueFromClient(clientUpdate);
        CommunicationWrapper.send(cohorts.get(3), new MessageCommand(MessageTypes.TEST_UPDATE_DURING_ELECTION));

        InUtils.threadSleep(13000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int N_COHORTS = dotenv.getNCohorts();
        int expected = 3 * (N_COHORTS - 2) + 1 + 1 + 1; // (N_COHORTS - 2) crash detect + (N_COHORTS - 2) start election + 1 update req + 1 update req received + 1 leader found + (N_COHORTS - 2) updates done
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        // check crash detection
        UpdateIdentifier checkUpdate = new UpdateIdentifier(1, 1);
        int detectedCrashes = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.COHORT_DETECTS_COHORT_CRASH && logEntry.secondActor.equals(firstCrash.path().name())) {
                detectedCrashes++;
            }
        }
        assertEquals(N_COHORTS - 2, detectedCrashes, "There should be " + (N_COHORTS - 2) + " detected crash");

        // check leader election starts
        int leaderElectionStarts = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.LEADER_ELECTION_START && logEntry.secondActor.equals(firstCrash.path().name())) {
                leaderElectionStarts++;
            }
        }
        assertEquals(N_COHORTS - 2, leaderElectionStarts, "There should be " + (N_COHORTS - 2) + " leader election starts");

        // check update request
        boolean updateRequestFound = false;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.UPDATE_REQ && logEntry.firstActor.equals(clientUpdate.path().name()) && logEntry.value == updateVal) {
                updateRequestFound = true;
                break;
            }
        }
        assertTrue(updateRequestFound, "There should be 1 update request from " + clientUpdate.path().name() + " to " + cohortUpdate + " with value " + updateVal);

        // check update request received
        boolean updateRequestReceived = false;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.COHORT_RECEIVED_UPDATE_REQUEST_DURING_ELECTION && logEntry.firstActor.equals(cohortUpdate) && logEntry.secondActor.equals(clientUpdate.path().name())) {
                updateRequestReceived = true;
                break;
            }
        }
        assertTrue(updateRequestReceived, "There should be 1 update request received");

        // check leader found
        boolean leaderFound = false;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.LEADER_FOUND && logEntry.firstActor.equals(newLeader.path().name())) {
                leaderFound = true;
                break;
            }
        }
        assertTrue(leaderFound, newLeader.path().name() + " should be the leader");

        // check updates done
        int updatesDone = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.UPDATE && logEntry.updateIdentifier.equals(checkUpdate) && logEntry.value == updateVal) {
                updatesDone++;
            }
        }
        assertEquals(N_COHORTS - 2, updatesDone, "There should be " + (N_COHORTS - 2) + " updates done");

        dotenv.setHeartbeat(prevHeartbeat);
        dotenv.setHeartbeatTimeout(prevHeartbeatTimeout);
    }
}
