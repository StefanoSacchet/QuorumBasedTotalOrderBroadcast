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

public class TestReplicaCrashNoACK {

    private static DotenvLoader dotenv;
    private static ActorRef clientUpdate1;
    private static String cohortUpdate1;
    private static int updateVal1;

    private static final int crashedCohorts = 2;

    @BeforeAll
    static void setUp() throws InterruptedException {
        dotenv = DotenvLoader.getInstance();

        InUtils inUtils = new InUtils();
        List<ActorRef> cohorts = inUtils.cohorts;
        List<ActorRef> clients = inUtils.clients;
        ActorSystem system = inUtils.system;

        InUtils.threadSleep(1000);

        // make two given cohorts crash
        CommunicationWrapper.send(cohorts.get(1), new MessageCommand(MessageTypes.CRASH));
        CommunicationWrapper.send(cohorts.get(3), new MessageCommand(MessageTypes.CRASH));

        InUtils.threadSleep(1000);

        clientUpdate1 = clients.get(2);
        cohortUpdate1 = TestUtils.getCohortFromClient(clientUpdate1);
        updateVal1 = TestUtils.getUpdateValueFromClient(clientUpdate1);
        CommunicationWrapper.send(clientUpdate1, new MessageCommand(MessageTypes.TEST_UPDATE));

        InUtils.threadSleep(3000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int N_COHORTS = dotenv.getNCohorts();
        int expected = 1 + (N_COHORTS - 2) + crashedCohorts; // 1 update req + (N_COHORTS - 2) update done + crashedCohorts crash detect
        assertEquals(logEntries.size(), expected, "There should be " + expected + " log entries");

        // check update request
        boolean updateRequestFound = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE_REQ && entry.firstActor.equals(clientUpdate1.path().name()) && entry.secondActor.equals(cohortUpdate1) && entry.value == updateVal1) {
                updateRequestFound = true;
                break;
            }
        }
        assertTrue(updateRequestFound, "Update request from " + clientUpdate1.path().name() + " to " + cohortUpdate1 + " with value " + updateVal1 + " should be found");

        // check update done
        UpdateIdentifier check = new UpdateIdentifier(0, 1);
        int updatesDone = 0;
        int cohortsAlive = N_COHORTS - crashedCohorts; // two are crashed!!!
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE && entry.updateIdentifier.equals(check) && updateVal1 == entry.value) {
                updatesDone++;
            }
        }
        assertEquals(updatesDone, cohortsAlive, "There should be " + cohortsAlive + " update messages");

        // check crash detect
        int crashDetected = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.COHORT_DETECTS_COHORT_CRASH && entry.firstActor.equals("cohort_0") && MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.ACK) {
                crashDetected++;
            }
        }
        assertEquals(crashedCohorts, crashDetected, "Coordinator crash due to no response to update request should be found");
    }
}
