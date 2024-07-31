package it.unitn.ds1.tests.arbitrary_cohorts_num;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import it.unitn.ds1.loggers.LogParser;
import it.unitn.ds1.loggers.LogType;
import it.unitn.ds1.messages.MessageCommand;
import it.unitn.ds1.messages.MessageTypes;
import it.unitn.ds1.tools.CommunicationWrapper;
import it.unitn.ds1.tools.DotenvLoader;
import it.unitn.ds1.tools.TestUtils;
import it.unitn.ds1.tools.InUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFlush {

    private static DotenvLoader dotenv;
    private static ActorRef clientReq1;
    private static ActorRef clientReq2;
    private static String cohortReq1;
    private static String cohortReq2;
    private static int updateValue1;
    private static int updateValue2;

    @BeforeAll
    static void setUp() throws InterruptedException {
        dotenv = DotenvLoader.getInstance();

        InUtils inUtils = new InUtils();
        List<ActorRef> cohorts = inUtils.cohorts;
        List<ActorRef> clients = inUtils.clients;
        ActorSystem system = inUtils.system;

        InUtils.threadSleep(1000);

        // make a given cohort crash
        CommunicationWrapper.send(cohorts.get(0), new MessageCommand(MessageTypes.CRASH_ONLY_ONE_WRITEOK));

        InUtils.threadSleep(1000);

        clientReq1 = clients.get(1);
        cohortReq1 = TestUtils.getCohortFromClient(clientReq1);
        updateValue1 = TestUtils.getUpdateValueFromClient(clientReq1);
        CommunicationWrapper.send(clientReq1, new MessageCommand(MessageTypes.TEST_UPDATE));

        InUtils.threadSleep(6500);

        // used to check if the system is still working after a coordinator crash
        clientReq2 = clients.get(2);
        cohortReq2 = TestUtils.getCohortFromClient(clientReq2);
        updateValue2 = TestUtils.getUpdateValueFromClient(clientReq2);
        CommunicationWrapper.send(clientReq2, new MessageCommand(MessageTypes.TEST_UPDATE));

        InUtils.threadSleep(4000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int N_COHORTS = dotenv.getNCohorts();
        int expected = 2 + 2 + 1 + 3 * (N_COHORTS - 1) + (N_COHORTS - 2); // 2 client update_req + 2 update done + (N_COHORTS - 1) detect crash + (N_COHORTS - 1) start election + 1 leader found + (N_COHORTS - 2) flush + (N_COHORTS - 1) updates
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        // check for read req and read done
        boolean updateRequestFound = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE_REQ && entry.firstActor.equals(clientReq1.path().name()) && entry.secondActor.equals(cohortReq1) && entry.value == updateValue1) {
                updateRequestFound = true;
            }
        }
        assertTrue(updateRequestFound, "There should be an update request from " + clientReq1 + " to " + cohortReq1 + " with value " + updateValue1);

        int updateDoneCount = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE && entry.value == updateValue1 && entry.updateIdentifier.getEpoch() == 0 && entry.updateIdentifier.getSequence() == 1) {
                updateDoneCount++;
            }
        }
        assertEquals(2, updateDoneCount, "There should be 2 update done entries with value " + updateValue1 + " and epoch 0 and sequence 1");

        int detectedCrashes = 0;
        int startElectionCount = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.COHORT_DETECTS_COHORT_CRASH && MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.WRITEOK) {
                detectedCrashes++;
                // here cohort_1 detects the crash because cohort_4 send it an election msg
            } else if (entry.type == LogType.COHORT_DETECTS_COHORT_CRASH && MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.ELECTION) {
                detectedCrashes++;
            } else if (entry.type == LogType.LEADER_ELECTION_START && entry.secondActor.equals("cohort_0")) {
                startElectionCount++;
            }
        }
        assertEquals(N_COHORTS - 1, detectedCrashes, "There should be " + (N_COHORTS - 1) + " detected crashes");
        assertEquals(N_COHORTS - 1, startElectionCount, "There should be " + (N_COHORTS - 1) + " election starting");

        String leader = "";
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.LEADER_FOUND) {
                leader = entry.firstActor;
                break;
            }
        }
        assertEquals("cohort_1", leader, "Leader should be cohort_1");

        int flushCount = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.FLUSH && entry.oldState == 0 && entry.value == updateValue1) {
                flushCount++;
            }
        }
        assertEquals(N_COHORTS - 2, flushCount, "There should be " + (N_COHORTS - 2) + " flushes with value " + updateValue1 + " and old state 0");

        // check for update
        updateRequestFound = false;
        int updateCount = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE && entry.value == updateValue2 && entry.updateIdentifier.getEpoch() == 1 && entry.updateIdentifier.getSequence() == 1) {
                updateCount++;
            } else if (entry.type == LogType.UPDATE_REQ && entry.value == updateValue2 && entry.secondActor.equals(cohortReq2)) {
                updateRequestFound = true;
            }
        }
        assert updateRequestFound : "There should be an update request from " + clientReq2 + " to " + cohortReq2 + " with value " + updateValue2;
        assertEquals(N_COHORTS - 1, updateCount, "There should be " + (N_COHORTS - 1) + " updates with value " + updateValue2 + " and epoch 1 and sequence 1");
    }
}
