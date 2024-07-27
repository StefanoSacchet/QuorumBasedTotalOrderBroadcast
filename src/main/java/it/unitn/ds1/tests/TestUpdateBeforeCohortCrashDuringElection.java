package it.unitn.ds1.tests;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import it.unitn.ds1.UpdateIdentifier;
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

public class TestUpdateBeforeCohortCrashDuringElection {

    @BeforeAll
    static void setUp() throws InterruptedException {
        InUtils testUtils = new InUtils();
        List<ActorRef> cohorts = testUtils.cohorts;
        List<ActorRef> clients = testUtils.clients;
        ActorSystem system = testUtils.system;

        InUtils.threadSleep(2000);
        CommunicationWrapper.send(cohorts.get(0), new MessageCommand(MessageTypes.CRASH));
        CommunicationWrapper.send(cohorts.get(2), new MessageCommand(MessageTypes.CRASH));

        CommunicationWrapper.send(clients.get(3), new MessageCommand(MessageTypes.TEST_UPDATE));
        InUtils.threadSleep(8000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int expected = 9; // 1 update request, 1 detect craash, 3 leader start, 1 leader found, 3 updates done
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        //check for read req and read done
        boolean updateRequestFound = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE_REQ && entry.firstActor.equals("client_3") && entry.value == 3000000) {
                updateRequestFound = true;
                break;
            }
        }
        assertTrue(updateRequestFound, "There should be an update request from client_3 to cohort_3 with value 3000000");

        boolean crashDetected = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.COHORT_DETECTS_COHORT_CRASH && entry.firstActor.equals("cohort_3") && entry.causeOfCrash.equals("UPDATE")) {
                crashDetected = true;
                break;
            }
        }
        assertTrue(crashDetected, "There should be a cohort_3 detecting cohort_1 crash");

        int leaderStarts = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.LEADER_ELECTION_START) {
                leaderStarts++;
            }
        }
        assertEquals(3, leaderStarts, "There should be 3 leader election starts");

        boolean leaderFound = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.LEADER_FOUND && entry.firstActor.equals("cohort_4")) {
                leaderFound = true;
                break;
            }
        }
        assertTrue(leaderFound, "There should be a leader found for cohort_4");

        int updatesDone = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE && entry.updateIdentifier.equals(new UpdateIdentifier(1,1)) && entry.value == 3000000) {
                updatesDone++;
            }
        }
        assertEquals(3, updatesDone, "There should be 3 updates done by cohort_4 with value 3000000");

    }
}
