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

public class TestFlush {

    @BeforeAll
    static void setUp() throws InterruptedException {
        InUtils inUtils = new InUtils();
        List<ActorRef> cohorts = inUtils.cohorts;
        List<ActorRef> clients = inUtils.clients;
        ActorSystem system = inUtils.system;

        // make a given cohort crash
        CommunicationWrapper.send(cohorts.get(0), new MessageCommand(MessageTypes.CRASH_ONLY_ONE_WRITEOK));

        InUtils.threadSleep(1000);

        CommunicationWrapper.send(clients.get(1), new MessageCommand(MessageTypes.TEST_UPDATE));

        InUtils.threadSleep(4000);

        // used to check if the system is still working after a coordinator crash
        CommunicationWrapper.send(clients.get(2), new MessageCommand(MessageTypes.TEST_UPDATE));

        InUtils.threadSleep(3000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int expected = 20; // 1 client update req + 2 cohorts update done + 4 cohorts detect crash + 4 cohorts start election + 1 leader found + 3 flush + 4 updates + 1 client update req + 4 cohorts update done
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        //check for read req and read done
        boolean updateRequestFound = false;
        int updateValue = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE_REQ && entry.firstActor.equals("client_1") && entry.secondActor.equals("cohort_1") && entry.value == 1000000) {
                updateRequestFound = true;
                updateValue = entry.value;
            }
        }
        assertTrue(updateRequestFound, "There should be an update request from client_2 to cohort_2 with value 2000000");
        assertEquals(1000000, updateValue, "The value of the update request should be 2000000");

        int updateDoneCount = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE && entry.value == 1000000 && entry.updateIdentifier.getEpoch() == 0 && entry.updateIdentifier.getSequence() == 1) {
                updateDoneCount++;
            }
        }
        assertEquals(2, updateDoneCount, "There should be 2 update done entries with value 2000000 and epoch 0 and sequence 1");

        int detectedCrashes = 0;
        int startElectionCount = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.COHORT_DETECTS_COHORT_CRASH && MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.WRITEOK) {
                detectedCrashes++;
                // here cohort_1 detects the crash because cohort_4 send it an election msg
            } else if (entry.type == LogType.COHORT_DETECTS_COHORT_CRASH && MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.ELECTION) {
                detectedCrashes++;
            } else if (entry.type == LogType.LEADER_ELECTION_START && entry.secondActor.equals("cohort_0")) {
                startElectionCount++;
            }
        }
        assertEquals(4, detectedCrashes, "There should be 4 detected crashes, because 4 replicas are alive");
        assertEquals(4, startElectionCount, "There should be 4 election starting, because 4 replicas are alive");

        String leader = "";
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.LEADER_FOUND) {
                leader = entry.firstActor;
                break;
            }
        }
        assertEquals("cohort_1", leader, "Leader should be cohort_1");

        int flushCount = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.FLUSH && entry.oldState == 0 && entry.value == 1000000) {
                flushCount++;
            }
        }
        assertEquals(3, flushCount, "There should be 3 flushes with value 2000000 and old state 0");

        // check for update
        updateRequestFound = false;
        int updateCount = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE && entry.value == 2000000 && entry.updateIdentifier.getEpoch() == 1 && entry.updateIdentifier.getSequence() == 1) {
                updateCount++;
            } else if (entry.type == LogType.UPDATE_REQ && entry.value == 2000000 && entry.secondActor.equals("cohort_2")) {
                updateRequestFound = true;
            }
        }
        assert updateRequestFound : "There should be an update request from cohort_1 to cohort_2 with value 2000000";
        assertEquals(4, updateCount, "There should be 4 updates with value 2000000 and epoch 1 and sequence 1");
    }
}
