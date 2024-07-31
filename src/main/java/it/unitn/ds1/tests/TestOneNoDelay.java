package it.unitn.ds1.tests;

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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;


public class TestOneNoDelay {

    @BeforeAll
    static void setUp() throws InterruptedException {
        InUtils inUtils = new InUtils();
        List<ActorRef> cohorts = inUtils.cohorts;
        List<ActorRef> clients = inUtils.clients;
        ActorSystem system = inUtils.system;

        CommunicationWrapper.send(clients.get(2), new MessageCommand(MessageTypes.TEST_UPDATE));
        CommunicationWrapper.send(clients.get(2), new MessageCommand(MessageTypes.TEST_UPDATE));

        InUtils.threadSleep(3000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int N_COHORTS = DotenvLoader.getInstance().getNCohorts();
        int expected = N_COHORTS * 2 + 2; // 2 updates

        assertEquals(expected, logEntries.size(), "There should be" + expected + " log entries");

        //check update request and crash detected
        int updateRequestFound = 0;
        int updateValue = -1;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE_REQ && entry.firstActor.equals("client_2") && entry.secondActor.equals("cohort_2")) {
                updateRequestFound++;
                updateValue = entry.value;
            }
        }
        assertEquals(updateValue, 2000000, "Update value should be 2000000");
        assertEquals(updateRequestFound, 2, "Update request should be found");

        //now we check for the updates
        UpdateIdentifier check = new UpdateIdentifier(0, 1);
        UpdateIdentifier check2 = new UpdateIdentifier(0, 2);
        int updatesDone = 0;
        int updatesDone2 = 0;
        //check for N_COHORTS update messages
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE && entry.updateIdentifier.equals(check) && updateValue == entry.value) {
                updatesDone++;
            } else if ((entry.type == LogType.UPDATE && entry.updateIdentifier.equals(check2) && updateValue == entry.value)) {
                updatesDone2++;
            }

        }
        assertEquals(updatesDone, N_COHORTS, "There should be " + N_COHORTS + " update messages");
        assertEquals(updatesDone2, N_COHORTS, "There should be " + N_COHORTS + " update messages");
    }
}
