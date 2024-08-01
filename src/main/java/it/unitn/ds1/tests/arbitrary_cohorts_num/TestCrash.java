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

public class TestCrash {

    private static DotenvLoader dotenv;
    private static ActorRef clientRead1;
    private static ActorRef clientUpdate1;
    private static ActorRef clientRead2;
    private static String cohortRead1;
    private static String cohortUpdate1;
    private static String cohortRead2;
    private static int updateVal;

    @BeforeAll
    static void setUp() throws InterruptedException {
        dotenv = DotenvLoader.getInstance();

        InUtils inUtils = new InUtils();
        List<ActorRef> cohorts = inUtils.cohorts;
        List<ActorRef> clients = inUtils.clients;
        ActorSystem system = inUtils.system;

        clientRead1 = clients.get(2);
        cohortRead1 = TestUtils.getCohortFromClient(clientRead1);
        CommunicationWrapper.send(clientRead1, new MessageCommand(MessageTypes.TEST_READ));

        InUtils.threadSleep(1000);

        clientUpdate1 = clients.get(2);
        cohortUpdate1 = TestUtils.getCohortFromClient(clientUpdate1);
        updateVal = TestUtils.getUpdateValueFromClient(clientUpdate1);
        CommunicationWrapper.send(clients.get(2), new MessageCommand(MessageTypes.TEST_UPDATE));

        InUtils.threadSleep(2000);

        // make a given cohort crash
        CommunicationWrapper.send(cohorts.get(2), new MessageCommand(MessageTypes.CRASH));

        clientRead2 = clients.get(2);
        cohortRead2 = TestUtils.getCohortFromClient(clientRead2);
        CommunicationWrapper.send(clientRead2, new MessageCommand(MessageTypes.TEST_READ));

        InUtils.threadSleep(3000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int N_COHORTS = dotenv.getNCohorts();
        int expected = 1 + 1 + 1 + N_COHORTS + 1 + 1; // 1 read req + 1 read done + 1 update req + N_COHORTS update done + 1 read req + 1 read done
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        // check read req and read done
        boolean readRequestFound = false;
        boolean readDoneFound = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.READ_REQ && entry.firstActor.equals(clientRead1.path().name()) && entry.secondActor.equals(cohortRead1)) {
                readRequestFound = true;
            } else if (entry.type == LogType.READ_DONE && entry.firstActor.equals(clientRead1.path().name()) && entry.value == 0) {
                readDoneFound = true;
            }
        }
        assertTrue(readRequestFound, "Read request should be found");
        assertTrue(readDoneFound, "Read done should be found");

        // check update request and crash detected
        boolean updateRequestFound = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE_REQ && entry.firstActor.equals(clientUpdate1.path().name()) && entry.secondActor.equals(cohortUpdate1) && entry.value == updateVal) {
                updateRequestFound = true;
                break;
            }
        }
        assertTrue(updateRequestFound, "Update request should be found with value " + updateVal);

        // now we check for the updates
        UpdateIdentifier check = new UpdateIdentifier(0, 1);
        int updatesDone = 0;
        //check for N_COHORTS update messages
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE && entry.updateIdentifier.equals(check) && entry.value == updateVal) {
                updatesDone++;
            }
        }
        assertEquals(updatesDone, N_COHORTS, "There should be " + N_COHORTS + " update messages");

        // now for the new read request with the crash found
        boolean readRequestFound2 = false;
        boolean crashDetected = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.READ_REQ && entry.firstActor.equals(clientRead2.path().name()) && entry.secondActor.equals(cohortRead2)) {
                readRequestFound2 = true;
            } else if (entry.type == LogType.CLIENT_DETECTS_COHORT_CRASH && entry.firstActor.equals(clientRead2.path().name())) {
                crashDetected = true;
            }
        }
        assertTrue(readRequestFound2, "Read request should be found");
        assertTrue(crashDetected, "Crash detected should be found");
    }
}
