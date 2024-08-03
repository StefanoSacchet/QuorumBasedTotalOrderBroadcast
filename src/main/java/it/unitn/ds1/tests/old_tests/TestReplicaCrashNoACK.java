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

import static org.junit.jupiter.api.Assertions.*;

public class TestReplicaCrashNoACK {

    @BeforeAll
    static void setUp() throws InterruptedException {
        InUtils inUtils = new InUtils();
        List<ActorRef> cohorts = inUtils.cohorts;
        List<ActorRef> clients = inUtils.clients;
        ActorSystem system = inUtils.system;

        // make a given cohort crash
        CommunicationWrapper.send(cohorts.get(1), new MessageCommand(MessageTypes.CRASH));
        CommunicationWrapper.send(cohorts.get(3), new MessageCommand(MessageTypes.CRASH));

        InUtils.threadSleep(1000);

        CommunicationWrapper.send(clients.get(2), new MessageCommand(MessageTypes.TEST_UPDATE));

        InUtils.threadSleep(3000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int expected = 6;
        int crashedCohorts = 2;

        assertEquals(logEntries.size(), expected, "There should be " + expected + " log entries");
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

        UpdateIdentifier check = new UpdateIdentifier(0, 1);
        int updatesDone = 0;
        int N_COHORTS = DotenvLoader.getInstance().getNCohorts() - crashedCohorts; // two are crashed!!!
        //check for N_COHORTS update messages
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE && entry.updateIdentifier.equals(check) && updateValue == entry.value) {
                updatesDone++;
            }
        }
        assertEquals(updatesDone, N_COHORTS, "There should be " + N_COHORTS + " update messages");

        int crashDetected = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.COHORT_DETECTS_COHORT_CRASH && entry.firstActor.equals("cohort_0") && MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.ACK) {
                crashDetected++;
            }
        }
        assertEquals(crashedCohorts, crashDetected, "Coordinator crash due to no response to update request should be found");
    }
}
