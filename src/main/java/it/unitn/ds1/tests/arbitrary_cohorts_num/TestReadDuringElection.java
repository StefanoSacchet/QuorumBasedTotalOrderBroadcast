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
import it.unitn.ds1.tools.TestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestReadDuringElection {

    private static DotenvLoader dotenv;
    private static ActorRef clientRead1;
    private static String cohortRead1;
    private static final int readVal1 = 0;

    @BeforeAll
    static void setUp() throws InterruptedException {
        dotenv = DotenvLoader.getInstance();

        InUtils inUtils = new InUtils();
        List<ActorRef> clients = inUtils.clients;
        List<ActorRef> cohorts = inUtils.cohorts;
        ActorSystem system = inUtils.system;

        InUtils.threadSleep(1000);

        clientRead1 = clients.get(2);
        cohortRead1 = TestUtils.getCohortFromClient(clientRead1);
        CommunicationWrapper.send(cohorts.get(2), new MessageCommand(MessageTypes.TEST_READ_DURING_ELECTION));

        CommunicationWrapper.send(cohorts.get(0), new MessageCommand(MessageTypes.CRASH));

        InUtils.threadSleep(12000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int N_COHORTS = dotenv.getNCohorts();
        int expected = 2 * (N_COHORTS - 1) + 2 + 1 + 1; // (N_COHORTS - 1) crash detect + (N_COHORTS - 1) election start + 2 read request + 1 read done + 1 leader found
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        // check crash detect and election start
        int detectedCrashes = 0;
        int leaderElectionStarts = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.COHORT_DETECTS_COHORT_CRASH) {
                detectedCrashes++;
            } else if (logEntry.type == LogType.LEADER_ELECTION_START) {
                leaderElectionStarts++;
            }
        }
        assertEquals(N_COHORTS - 1, detectedCrashes, "There should be " + (N_COHORTS - 1) + " detected crashes");
        assertEquals(N_COHORTS - 1, leaderElectionStarts, "There should be " + (N_COHORTS - 1) + " leader election starts");

        // check read request
        int readRequests = 0;
        int readDoneDuringElection = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.READ_REQ) {
                readRequests++;
            } else if (logEntry.type == LogType.COHORT_RECEIVED_READ_REQUEST_DURING_ELECTION) {
                readDoneDuringElection++;
            }
        }
        assertEquals(1, readRequests, "There should be 1 read request");
        assertEquals(1, readDoneDuringElection, "There should be 1 read done during election");

        // check leader found
        boolean leaderFound = false;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.LEADER_FOUND) {
                leaderFound = true;
                break;
            }
        }
        assertTrue(leaderFound, "A leader should be found");
    }
}
