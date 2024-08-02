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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSubsequentCrashesDuringElection {

    private static DotenvLoader dotenv;
    private static ActorRef cohortCrash1;
    private static ActorRef cohortCrash2;
    private static ActorRef newLeader;

    private static int prevHeartbeat;
    private static int prevHeartbeatTimeout;

    @BeforeAll
    static void setUp() throws InterruptedException {
        dotenv = DotenvLoader.getInstance();

        prevHeartbeat = dotenv.getHeartbeat();
        prevHeartbeatTimeout = dotenv.getHeartbeatTimeout();
        dotenv.setHeartbeat(3500);
        dotenv.setHeartbeatTimeout(4000);

        InUtils testUtils = new InUtils();
        List<ActorRef> cohorts = testUtils.cohorts;
        List<ActorRef> clients = testUtils.clients;
        ActorSystem system = testUtils.system;

        InUtils.threadSleep(1000);

        cohortCrash1 = cohorts.get(0);
        CommunicationWrapper.send(cohortCrash1, new MessageCommand(MessageTypes.CRASH));

        cohortCrash2 = cohorts.get(cohorts.size() - 1);
        newLeader = cohorts.get(cohorts.size() - 2);
        CommunicationWrapper.send(cohortCrash2, new MessageCommand(MessageTypes.CRASH_DEADLOCK_ELECTION));

        InUtils.threadSleep(22000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int N_COHORTS = dotenv.getNCohorts();
        int expected = 2 * (N_COHORTS - 1) + 2 * (N_COHORTS - 2) + 1; // (N_COHORTS - 1) detect crash + (N_COHORTS - 1) leader start + (N_COHORTS - 2) deadlock noticed + (N_COHORTS - 2) start election + 1 leader found
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        // check crash detect and election start
        int detectedCrashes = 0;
        int leaderStarts = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.COHORT_DETECTS_COHORT_CRASH && logEntry.secondActor.equals(cohortCrash1.path().name())) {
                detectedCrashes++;
            } else if (logEntry.type == LogType.LEADER_ELECTION_START && logEntry.secondActor.equals(cohortCrash1.path().name())) {
                leaderStarts++;
            }
        }
        assertEquals(N_COHORTS - 1, detectedCrashes, "There should be " + (N_COHORTS - 1) + " detected crashes");
        assertEquals(N_COHORTS - 1, leaderStarts, "There should be " + (N_COHORTS - 1) + " leader starts");

        // check deadlock noticed and election start due to deadlock
        int deadlockNoticed = 0;
        int electionStartsDeadlock = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.DEADLOCK_DETECTED) {
                deadlockNoticed++;
            } else if (logEntry.type == LogType.LEADER_ELECTION_START_DEADLOCK) {
                electionStartsDeadlock++;
            }
        }
        assertEquals(N_COHORTS - 2, deadlockNoticed, "There should be " + (N_COHORTS - 2) + " deadlock noticed");
        assertEquals(N_COHORTS - 2, electionStartsDeadlock, "There should be " + (N_COHORTS - 2) + " election starts due to deadlock");

        // check leader found
        boolean leaderFound = false;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.LEADER_FOUND && logEntry.firstActor.equals(newLeader.path().name())) {
                leaderFound = true;
                break;
            }
        }
        assertTrue(leaderFound, newLeader.path().name() + " should be the leader");

        dotenv.setHeartbeat(prevHeartbeat);
        dotenv.setHeartbeatTimeout(prevHeartbeatTimeout);
    }
}
