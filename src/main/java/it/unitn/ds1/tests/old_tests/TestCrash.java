package it.unitn.ds1.tests.old_tests;

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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class TestCrash {

    @BeforeAll
    static void setUp() throws InterruptedException {
        InUtils inUtils = new InUtils();
        List<ActorRef> cohorts = inUtils.cohorts;
        List<ActorRef> clients = inUtils.clients;
        ActorSystem system = inUtils.system;

        // make a given cohort crash
        CommunicationWrapper.send(clients.get(2), new MessageCommand(MessageTypes.TEST_READ));

        InUtils.threadSleep(1000);

        CommunicationWrapper.send(clients.get(2), new MessageCommand(MessageTypes.TEST_UPDATE));

        InUtils.threadSleep(1000);

        // make a given cohort crash
        CommunicationWrapper.send(cohorts.get(2), new MessageCommand(MessageTypes.CRASH));

        CommunicationWrapper.send(clients.get(2), new MessageCommand(MessageTypes.TEST_READ));

        InUtils.threadSleep(3000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int expected = 10; // 2 for read req and response, 1 for update req, N_COHORTS for the update, 2 for read req and response
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        //check read req and read done
        boolean readRequestFound = false;
        boolean readDoneFound = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.READ_REQ && entry.firstActor.equals("client_2") && entry.secondActor.equals("cohort_2")) {
                readRequestFound = true;
            } else if (entry.type == LogType.READ_DONE && entry.firstActor.equals("client_2") && entry.value == 0) {
                readDoneFound = true;
            }
        }
        assertTrue(readRequestFound, "Read request should be found");
        assertTrue(readDoneFound, "Read done should be found");

        //check update request and crash detected
        boolean updateRequestFound = false;
        int updateValue = -1;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE_REQ && entry.firstActor.equals("client_2") && entry.secondActor.equals("cohort_2")) {
                updateRequestFound = true;
                updateValue = entry.value;
                break;
            }
        }
        assertEquals(updateValue, 2000000, "Update value should be 2000000");
        assertTrue(updateRequestFound, "Update request should be found");

        //now we check for the updates
        UpdateIdentifier check = new UpdateIdentifier(0, 1);
        int updatesDone = 0;
        int N_COHORTS = DotenvLoader.getInstance().getNCohorts();
        //check for N_COHORTS update messages
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE && entry.updateIdentifier.equals(check) && updateValue == entry.value) {
                updatesDone++;
            }
        }
        assertEquals(updatesDone, N_COHORTS, "There should be " + N_COHORTS + " update messages");

        //now for the new read request with the crash found
        boolean readRequestFound2 = false;
        boolean crashDetected = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.READ_REQ && entry.firstActor.equals("client_2") && entry.secondActor.equals("cohort_2")) {
                readRequestFound2 = true;
            } else if (entry.type == LogType.CLIENT_DETECTS_COHORT_CRASH && entry.firstActor.equals("client_2")) {
                crashDetected = true;
            }
        }
        assertTrue(readRequestFound2, "Read request should be found");
        assertTrue(crashDetected, "Crash detected should be found");
    }
}
