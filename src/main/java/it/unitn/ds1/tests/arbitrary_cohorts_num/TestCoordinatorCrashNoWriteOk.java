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

public class TestCoordinatorCrashNoWriteOk {

    private static DotenvLoader dotenv;
    private static ActorRef clientReq;
    private static String cohortReq;
    private static int updateVal;

    @BeforeAll
    static void setUp() throws InterruptedException {
        dotenv = DotenvLoader.getInstance();

        InUtils inUtils = new InUtils();
        List<ActorRef> clients = inUtils.clients;
        List<ActorRef> cohorts = inUtils.cohorts;
        ActorSystem system = inUtils.system;

        InUtils.threadSleep(1000);

        clientReq = clients.get(2);
        cohortReq = TestUtils.getCohortFromClient(clientReq);
        updateVal = TestUtils.getUpdateValueFromClient(clientReq);
        CommunicationWrapper.send(clients.get(2), new MessageCommand(MessageTypes.TEST_UPDATE));
        CommunicationWrapper.send(cohorts.get(0), new MessageCommand(MessageTypes.CRASH_NO_WRITEOK));

        InUtils.threadSleep(10000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int N_COHORTS = dotenv.getNCohorts();
        int expected = 1 + 1 + 3 * (N_COHORTS - 1); // 1 update_req + (N_COHORTS - 1) crash detect + (N_COHORTS - 1) start election + 1 leader found + (N_COHORTS - 1) update done
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        // check for read req and read done
        boolean updateRequestFound = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE_REQ && entry.firstActor.equals(clientReq.path().name()) && entry.secondActor.equals(cohortReq) && entry.value == updateVal) {
                updateRequestFound = true;
            }
        }
        assertTrue(updateRequestFound, "There should be an update request from " + clientReq + " to " + cohortReq + " with value " + updateVal);

        int detectedCrashes = 0;
        int startElectionCount = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.COHORT_DETECTS_COHORT_CRASH && (MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.WRITEOK || MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.ELECTION)) {
                detectedCrashes++;
            } else if (entry.type == LogType.LEADER_ELECTION_START && entry.secondActor.equals("cohort_0")) {
                startElectionCount++;
            }
        }
        assertEquals(N_COHORTS - 1, detectedCrashes, "There should be " + (N_COHORTS - 1) + " detected crashes");
        assertEquals(N_COHORTS - 1, startElectionCount, "There should be " + (N_COHORTS - 1) + " election starting");

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
            if (entry.type == LogType.UPDATE && entry.value == updateVal && entry.updateIdentifier.equals(check)) {
                updatesFound++;
            }
        }
        assertEquals(N_COHORTS - 1, updatesFound, "There should be " + (N_COHORTS - 1) + " updates done with value " + updateVal + " and updateID " + updateVal);
    }
}
