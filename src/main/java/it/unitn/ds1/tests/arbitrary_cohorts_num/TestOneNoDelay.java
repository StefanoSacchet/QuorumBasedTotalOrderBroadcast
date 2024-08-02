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

public class TestOneNoDelay {

    private static DotenvLoader dotenv;
    private static ActorRef clientUpdate1;
    private static ActorRef clientUpdate2;
    private static String cohortUpdate1;
    private static String cohortUpdate2;
    private static int updateVal1;
    private static int updateVal2;

    private static int prevTimeout;

    @BeforeAll
    static void setUp() throws InterruptedException {
        dotenv = DotenvLoader.getInstance();
        prevTimeout = dotenv.getTimeout();
        dotenv.setTimeout(4500);

        InUtils inUtils = new InUtils();
        List<ActorRef> cohorts = inUtils.cohorts;
        List<ActorRef> clients = inUtils.clients;
        ActorSystem system = inUtils.system;

        InUtils.threadSleep(1000);

        clientUpdate1 = clients.get(2);
        cohortUpdate1 = TestUtils.getCohortFromClient(clientUpdate1);
        updateVal1 = TestUtils.getUpdateValueFromClient(clientUpdate1);
        CommunicationWrapper.send(clientUpdate1, new MessageCommand(MessageTypes.TEST_UPDATE));

        clientUpdate2 = clients.get(2);
        cohortUpdate2 = TestUtils.getCohortFromClient(clientUpdate2);
        updateVal2 = TestUtils.getUpdateValueFromClient(clientUpdate2);
        CommunicationWrapper.send(clientUpdate2, new MessageCommand(MessageTypes.TEST_UPDATE));

        InUtils.threadSleep(4500);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int N_COHORTS = dotenv.getNCohorts();
        int expected = N_COHORTS * 2 + 2; // 2 updates
        assertEquals(expected, logEntries.size(), "There should be" + expected + " log entries");

        // check update requests
        boolean updateRequestFound1 = false;
        boolean updateRequestFound2 = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE_REQ && entry.firstActor.equals(clientUpdate1.path().name()) && entry.secondActor.equals(cohortUpdate1) && entry.value == updateVal1 && !updateRequestFound1) {
                updateRequestFound1 = true;
            } else if (entry.type == LogType.UPDATE_REQ && entry.firstActor.equals(clientUpdate2.path().name()) && entry.secondActor.equals(cohortUpdate2) && entry.value == updateVal2) {
                updateRequestFound2 = true;
            }
        }
        assertTrue(updateRequestFound1, "Update request from " + clientUpdate1.path().name() + " to " + cohortUpdate1 + " with value " + updateVal1 + " should be found");
        assertTrue(updateRequestFound2, "Update request from " + clientUpdate2.path().name() + " to " + cohortUpdate2 + " with value " + updateVal2 + " should be found");

        // now we check for the updates
        UpdateIdentifier check = new UpdateIdentifier(0, 1);
        UpdateIdentifier check2 = new UpdateIdentifier(0, 2);
        int updatesDone = 0;
        int updatesDone2 = 0;
        // check for N_COHORTS update messages
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE && entry.updateIdentifier.equals(check) && updateVal1 == entry.value) {
                updatesDone++;
            } else if ((entry.type == LogType.UPDATE && entry.updateIdentifier.equals(check2) && updateVal2 == entry.value)) {
                updatesDone2++;
            }

        }
        assertEquals(updatesDone, N_COHORTS, "There should be " + N_COHORTS + " update messages");
        assertEquals(updatesDone2, N_COHORTS, "There should be " + N_COHORTS + " update messages");

        dotenv.setTimeout(prevTimeout);
    }
}
