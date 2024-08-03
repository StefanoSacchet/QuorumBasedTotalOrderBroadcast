package it.unitn.ds1.tests.arbitrary_cohorts_num;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
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

public class TestCoordinatorCrashNoUpdate {

    private static DotenvLoader dotenv;
    private static ActorRef clientUpdate1;
    private static ActorRef clientUpdate2;
    private static String cohortUpdate1;
    private static String cohortUpdate2;
    private static int updateVal1;
    private static int updateVal2;
    private static ActorRef cohortCrash;

    private static int prevHeartbeat;
    private static int prevHeartbeatTimeout;
    private static int prevTimeout;

    @BeforeAll
    static void setUp() throws InterruptedException {
        dotenv = DotenvLoader.getInstance();

        prevHeartbeat = dotenv.getHeartbeat();
        prevHeartbeatTimeout = dotenv.getHeartbeatTimeout();
        prevTimeout = dotenv.getTimeout();

        dotenv.setHeartbeat(4000);
        dotenv.setHeartbeatTimeout(dotenv.getHeartbeat() + 500);
        if (dotenv.getNCohorts() > 12) {
            dotenv.setTimeout(3000);
        }

        InUtils inUtils = new InUtils();
        List<ActorRef> clients = inUtils.clients;
        List<ActorRef> cohorts = inUtils.cohorts;
        ActorSystem system = inUtils.system;

        InUtils.threadSleep(1000);

        clientUpdate1 = clients.get(2);
        cohortUpdate1 = TestUtils.getCohortFromClient(clientUpdate1);
        updateVal1 = TestUtils.getUpdateValueFromClient(clientUpdate1);
        CommunicationWrapper.send(cohorts.get(2), new MessageCommand(MessageTypes.TEST_UPDATE_DURING_ELECTION));

        clientUpdate2 = clients.get(3);
        cohortUpdate2 = TestUtils.getCohortFromClient(clientUpdate2);
        updateVal2 = TestUtils.getUpdateValueFromClient(clientUpdate2);
        CommunicationWrapper.send(cohorts.get(3), new MessageCommand(MessageTypes.TEST_UPDATE_DURING_ELECTION));

        cohortCrash = cohorts.get(0);
        CommunicationWrapper.send(cohortCrash, new MessageCommand(MessageTypes.CRASH));

        InUtils.threadSleep(13000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int N_COHORTS = dotenv.getNCohorts();
        int expected = 4 * (N_COHORTS - 1) + 2 + 2 + 1; // (N_COHORTS - 1) crash detect + (N_COHORTS - 1) election start + 2 update req + 2 update received + 2 * (N_COHORTS - 1) update done + 1 leader found
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        // check crash detect and election start
        int detectedCrashes = 0;
        int leaderElectionStarts = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.COHORT_DETECTS_COHORT_CRASH && logEntry.secondActor.equals(cohortCrash.path().name())) {
                detectedCrashes++;
            } else if (logEntry.type == LogType.LEADER_ELECTION_START && logEntry.secondActor.equals(cohortCrash.path().name())) {
                leaderElectionStarts++;
            }
        }
        assertEquals(N_COHORTS - 1, detectedCrashes, "There should be " + (N_COHORTS - 1) + " detected crashes");
        assertEquals(N_COHORTS - 1, leaderElectionStarts, "There should be " + (N_COHORTS - 1) + " leader election starts");

        // check update requests
        int updateRequests = 0;
        int updateReceived = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.UPDATE_REQ && (logEntry.firstActor.equals(clientUpdate1.path().name()) || logEntry.firstActor.equals(clientUpdate2.path().name()))) {
                updateRequests++;
            } else if (logEntry.type == LogType.COHORT_RECEIVED_UPDATE_REQUEST_DURING_ELECTION && (logEntry.firstActor.equals(cohortUpdate1) || logEntry.firstActor.equals(cohortUpdate2))) {
                updateReceived++;
            }
        }
        assertEquals(2, updateRequests, "There should be 2 update requests");
        assertEquals(2, updateReceived, "There should be 2 update received");

        // check leader found
        boolean leaderFound = false;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.LEADER_FOUND) {
                leaderFound = true;
                break;
            }
        }
        assertTrue(leaderFound, "Leader should be found");

        // check update done
        int updateDone1 = 0;
        int updateDone2 = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.UPDATE && logEntry.value == updateVal1) {
                updateDone1++;
            } else if (logEntry.type == LogType.UPDATE && logEntry.value == updateVal2) {
                updateDone2++;
            }
        }
        assertEquals(N_COHORTS - 1, updateDone1, "There should be " + (N_COHORTS - 1) + " update done for update value " + updateVal1);
        assertEquals(N_COHORTS - 1, updateDone2, "There should be " + (N_COHORTS - 1) + " update done for update value " + updateVal2);

        dotenv.setHeartbeat(prevHeartbeat);
        dotenv.setHeartbeatTimeout(prevHeartbeatTimeout);
        dotenv.setTimeout(prevTimeout);
    }
}
