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

public class TestUpdateBeforeCohortCrashDuringElection {

    private static DotenvLoader dotenv;
    private static ActorRef clientUpdate;
    private static String cohortUpdate;
    private static int updateVal;
    private static ActorRef newLeader;

    @BeforeAll
    static void setUp() throws InterruptedException {
        dotenv = DotenvLoader.getInstance();

        InUtils testUtils = new InUtils();
        List<ActorRef> cohorts = testUtils.cohorts;
        List<ActorRef> clients = testUtils.clients;
        ActorSystem system = testUtils.system;

        newLeader = cohorts.get(cohorts.size() - 1);

        InUtils.threadSleep(1000);

        CommunicationWrapper.send(cohorts.get(0), new MessageCommand(MessageTypes.CRASH));

        CommunicationWrapper.send(cohorts.get(2), new MessageCommand(MessageTypes.CRASH));

        clientUpdate = clients.get(3);
        cohortUpdate = TestUtils.getCohortFromClient(clientUpdate);
        updateVal = TestUtils.getUpdateValueFromClient(clientUpdate);
        CommunicationWrapper.send(clients.get(3), new MessageCommand(MessageTypes.TEST_UPDATE));

        InUtils.threadSleep(13000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int N_COHORTS = dotenv.getNCohorts();
        int expected = 1 + 1 + 2 * (N_COHORTS - 2) + 1; // 1 update req + 1 detect crash + (N_COHORTS - 2) start election + 1 leader found + (N_COHORTS - 2) updates done
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        // check update request
        boolean updateRequestFound = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE_REQ && entry.firstActor.equals(clientUpdate.path().name()) && entry.secondActor.equals(cohortUpdate) && entry.value == updateVal) {
                updateRequestFound = true;
                break;
            }
        }
        assertTrue(updateRequestFound, "There should be an update request from " + clientUpdate.path().name() + " to " + cohortUpdate + " with value " + updateVal);

        // check crash detect
        boolean crashDetected = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.COHORT_DETECTS_COHORT_CRASH && entry.firstActor.equals(cohortUpdate) && entry.causeOfCrash.equals(MessageTypes.UPDATE.toString())) {
                crashDetected = true;
                break;
            }
        }
        assertTrue(crashDetected, "There should be " + cohortUpdate + " detecting coordinator crash");

        // check election start
        int electionStart = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.LEADER_ELECTION_START && entry.secondActor.equals("cohort_0")) {
                electionStart++;
            }
        }
        assertEquals(N_COHORTS - 2, electionStart, "There should be " + (N_COHORTS - 2) + " leader election start");

        // check leader found
        boolean leaderFound = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.LEADER_FOUND && entry.firstActor.equals(newLeader.path().name())) {
                leaderFound = true;
                break;
            }
        }
        assertTrue(leaderFound, "New leader should be " + newLeader.path().name());

        // check updates done
        int updatesDone = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE && entry.updateIdentifier.equals(new UpdateIdentifier(1, 1)) && entry.value == updateVal) {
                updatesDone++;
            }
        }
        assertEquals(N_COHORTS - 2, updatesDone, "There should be " + (N_COHORTS - 2) + " updates done with value " + updateVal);
    }
}
