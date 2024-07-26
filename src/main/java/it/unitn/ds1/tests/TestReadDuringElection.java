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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestReadDuringElection {
    @BeforeAll
    static void setUp() throws InterruptedException {
        InUtils inUtils = new InUtils();
        List<ActorRef> clients = inUtils.clients;
        List<ActorRef> cohorts = inUtils.cohorts;
        ActorSystem system = inUtils.system;

        InUtils.threadSleep(1000);

        CommunicationWrapper.send(cohorts.get(2), new MessageCommand(MessageTypes.TEST_READ_DURING_ELECTION));
        CommunicationWrapper.send(cohorts.get(0), new MessageCommand(MessageTypes.CRASH));

        InUtils.threadSleep(6000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int expected = 12; // 4 detection + 4 leader election start + 1 read request + 1 read done + 1 leader found
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        int detectedCrashes = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.COHORT_DETECTS_COHORT_CRASH) {
                detectedCrashes++;
            }
        }
        assertEquals(4, detectedCrashes, "There should be 4 detected crashes");

        int leaderElectionStarts = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.LEADER_ELECTION_START) {
                leaderElectionStarts++;
            }
        }
        assertEquals(4, leaderElectionStarts, "There should be 4 leader election starts");

        int readRequests = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.READ_REQ) {
                readRequests++;
            }
        }
        assertEquals(1, readRequests, "There should be 1 read request");

        int readDonesDuringElection = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.COHORT_RECEIVED_READ_REQUEST_DURING_ELECTION) {
                readDonesDuringElection++;
            }
        }
        assertEquals(1, readDonesDuringElection, "There should be 1 read done during election");

        int leaderFounds = 0;
        for (LogParser.LogEntry logEntry : logEntries) {
            if (logEntry.type == LogType.LEADER_FOUND) {
                leaderFounds++;
            }
        }
        assertEquals(1, leaderFounds, "There should be 1 leader found");


    }

}
