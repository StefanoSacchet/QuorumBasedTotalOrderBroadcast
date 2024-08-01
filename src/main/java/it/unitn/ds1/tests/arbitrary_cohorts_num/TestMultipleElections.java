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

public class TestMultipleElections {

    private static DotenvLoader dotenv;
    private static ActorRef firstCrash;
    private static ActorRef secondCrash;
    private static ActorRef clientUpdate;
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

        // crash coordinator
        firstCrash = cohorts.get(0);
        CommunicationWrapper.send(firstCrash, new MessageCommand(MessageTypes.CRASH));

        // wait for the election to end
        InUtils.threadSleep(12500);

        // crash another coordinator
        secondCrash = cohorts.get(cohorts.size() - 1);
        CommunicationWrapper.send(secondCrash, new MessageCommand(MessageTypes.CRASH));

        // wait for the election to end
        InUtils.threadSleep(12500);

        // used to check if the system is still working after a coordinator crash
        clientUpdate = clients.get(2);
        cohortUpdate = TestUtils.getCohortFromClient(clientUpdate);
        updateVal = TestUtils.getUpdateValueFromClient(clientUpdate);
        CommunicationWrapper.send(clientUpdate, new MessageCommand(MessageTypes.TEST_UPDATE));

        InUtils.threadSleep(3000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int N_COHORTS = dotenv.getNCohorts();
        int expected = 2 * (N_COHORTS - 1) + 1 + 3 * (N_COHORTS - 2) + 1 + 1; // (N_COHORTS - 1) crash detect + (N_COHORTS - 1) start election + 1 leader found + (N_COHORTS - 2) crash detect + (N_COHORTS - 2) start election + 1 leader found + 1 update req + (N_COHORTS - 2) update done
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        int detectedCrashes = 0;
        int startElectionCount = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.COHORT_DETECTS_COHORT_CRASH && entry.secondActor.equals(firstCrash.path().name()) && (MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.HEARTBEAT || MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.ELECTION)) {
                detectedCrashes++;
            } else if (entry.type == LogType.LEADER_ELECTION_START && entry.secondActor.equals(firstCrash.path().name())) {
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

        detectedCrashes = 0;
        startElectionCount = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.COHORT_DETECTS_COHORT_CRASH && entry.secondActor.equals(secondCrash.path().name()) && (MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.HEARTBEAT || MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.ELECTION)) {
                detectedCrashes++;
            } else if (entry.type == LogType.LEADER_ELECTION_START && entry.secondActor.equals(secondCrash.path().name())) {
                startElectionCount++;
            }
        }
        assertEquals((N_COHORTS - 2), detectedCrashes, "There should be " + (N_COHORTS - 2) + " detected crashes");
        assertEquals((N_COHORTS - 2), startElectionCount, "There should be " + (N_COHORTS - 2) + " election starting");

        // check for update
        boolean updateRequestFound = false;
        int updateCount = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE && entry.value == updateVal && entry.updateIdentifier.getEpoch() == 2 && entry.updateIdentifier.getSequence() == 1) {
                updateCount++;
            } else if (entry.type == LogType.UPDATE_REQ && entry.value == updateVal && entry.firstActor.equals(clientUpdate.path().name()) && entry.secondActor.equals(cohortUpdate)) {
                updateRequestFound = true;
            }
        }
        assert updateRequestFound : "There should be an update request from " + clientUpdate.path().name() + " to cohort_2 with value " + updateVal;
        assertEquals(N_COHORTS - 2, updateCount, "There should be " + (N_COHORTS - 2) + " updates with value " + updateVal + " and epoch 2 and sequence 1");
    }
}
