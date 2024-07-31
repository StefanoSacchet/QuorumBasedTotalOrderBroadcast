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

public class TestCoordinatorCrashNoHeartBeat {

    private static DotenvLoader dotenv;

    @BeforeAll
    static void setUp() throws InterruptedException {
        dotenv = DotenvLoader.getInstance();

        InUtils inUtils = new InUtils();
        List<ActorRef> cohorts = inUtils.cohorts;
        List<ActorRef> clients = inUtils.clients;
        ActorSystem system = inUtils.system;

        InUtils.threadSleep(2000);

        CommunicationWrapper.send(cohorts.get(0), new MessageCommand(MessageTypes.CRASH));

        InUtils.threadSleep(10000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int N_COHORTS = dotenv.getNCohorts();
        int expected = (N_COHORTS - 1) * 2 + 1; // N_COHORTS - 1 crash detect, N_COHORTS - 1 election start, 1 leader found
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        // now we have to check the number of detected crashes and election starting
        int detectedCrashes = 0;
        int startElectionCount = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.COHORT_DETECTS_COHORT_CRASH && (MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.HEARTBEAT || MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.ELECTION)) {
                detectedCrashes++;
            } else if (entry.type == LogType.LEADER_ELECTION_START && entry.secondActor.equals("cohort_0")) {
                startElectionCount++;
            }
        }
        assertEquals(N_COHORTS - 1, detectedCrashes, "There should be " + (N_COHORTS - 1) + " detected crashes");
        assertEquals(N_COHORTS - 1, startElectionCount, "There should be " + (N_COHORTS - 1) + " election starting");

        //now we have to check the leader
        boolean leaderFound = false;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.LEADER_FOUND) {
                leaderFound = true;
                break;
            }
        }
        assertTrue(leaderFound, "Leader should be found");
    }
}
