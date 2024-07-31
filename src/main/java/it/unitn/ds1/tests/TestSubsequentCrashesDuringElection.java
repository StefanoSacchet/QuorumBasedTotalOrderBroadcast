package it.unitn.ds1.tests;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
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

public class TestSubsequentCrashesDuringElection {
    @BeforeAll
    static void setUp() throws InterruptedException {
        InUtils testUtils = new InUtils();
        List<ActorRef> cohorts = testUtils.cohorts;
        List<ActorRef> clients = testUtils.clients;
        ActorSystem system = testUtils.system;


        InUtils.threadSleep(2000);
        CommunicationWrapper.send(cohorts.get(0), new MessageCommand(MessageTypes.CRASH));
        CommunicationWrapper.send(cohorts.get(4), new MessageCommand(MessageTypes.CRASH_DEADLOCK_ELECTION));
        InUtils.threadSleep(15000);
        system.terminate();

        // dotenv.setNCohorts(oldNCohorts);
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int expected = 15; // 4 detect crash + 4 leader start + 3 deadlock noticed + 3 start election + 1 leader found
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        int detectedCrashes = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.COHORT_DETECTS_COHORT_CRASH) {
                detectedCrashes++;
            }
        }
        assertEquals(4, detectedCrashes, "There should be 4 detected crashes");

        int leaderStarts = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.LEADER_ELECTION_START) {
                leaderStarts++;
            }
        }
        assertEquals(4, leaderStarts, "There should be 4 leader starts");

        int deadlockNoticed = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.DEADLOCK_DETECTED) {
                deadlockNoticed++;
            }
        }
        assertEquals(3, deadlockNoticed, "There should be 3 deadlock noticed");

        int electionStartsDeadlock = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.LEADER_ELECTION_START_DEADLOCK) {
                electionStartsDeadlock++;
            }
        }
        assertEquals(3, electionStartsDeadlock, "There should be 3 election starts due to deadlock");

        boolean leaderFound = false;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.LEADER_FOUND && logEntry.firstActor.equals("cohort_3")) {
                leaderFound = true;
            }
        }
        assertTrue(leaderFound, "Cohort 3 should be the leader");
    }
}
