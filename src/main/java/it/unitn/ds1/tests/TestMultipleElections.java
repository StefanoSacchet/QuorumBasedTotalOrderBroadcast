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

public class TestMultipleElections {

    private static ActorRef firstCrash;
    private static ActorRef secondCrash;
    private static ActorRef clientRequest;

    private static int getUpdateValue(ActorRef clientRequest) {
        return Integer.parseInt(clientRequest.path().name().split("_")[1]) * 1000000;
    }

    @BeforeAll
    static void setUp() throws InterruptedException {

        InUtils inUtils = new InUtils();
        List<ActorRef> cohorts = inUtils.cohorts;
        List<ActorRef> clients = inUtils.clients;
        ActorSystem system = inUtils.system;

        InUtils.threadSleep(1000);

        // crash coordinator
        firstCrash = cohorts.get(0);
        CommunicationWrapper.send(firstCrash, new MessageCommand(MessageTypes.CRASH));

        InUtils.threadSleep(7000);

        // crash another coordinator
        secondCrash = cohorts.get(4);
        CommunicationWrapper.send(secondCrash, new MessageCommand(MessageTypes.CRASH));

        InUtils.threadSleep(7500);

        // used to check if the system is still working after a coordinator crash
        clientRequest = clients.get(2);
        CommunicationWrapper.send(clientRequest, new MessageCommand(MessageTypes.TEST_UPDATE));

        InUtils.threadSleep(3000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int expected = 20; // 4 crash detect + 4 start election + 1 leader found + 3 crash detect + 3 start election + 1 leader found + 1 client update req + 3 cohorts update done
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

        int detectedCrashes = 0;
        int startElectionCount = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.COHORT_DETECTS_COHORT_CRASH && MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.HEARTBEAT && entry.secondActor.equals(firstCrash.path().name())) {
                detectedCrashes++;
            } else if (entry.type == LogType.LEADER_ELECTION_START && entry.secondActor.equals(firstCrash.path().name())) {
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
        assertEquals("cohort_4", leader, "Leader should be cohort_4");

        detectedCrashes = 0;
        startElectionCount = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.COHORT_DETECTS_COHORT_CRASH && MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.HEARTBEAT && entry.secondActor.equals(secondCrash.path().name())) {
                detectedCrashes++;
            } else if (entry.type == LogType.LEADER_ELECTION_START && entry.secondActor.equals(secondCrash.path().name())) {
                startElectionCount++;
            }
        }
        assertEquals(3, detectedCrashes, "There should be 3 detected crashes, because 3 replicas are alive");
        assertEquals(3, startElectionCount, "There should be 3 election starting, because 3 replicas are alive");

        // check for update
        int updateValue = getUpdateValue(clientRequest);
        boolean updateRequestFound = false;
        int updateCount = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE && entry.value == updateValue && entry.updateIdentifier.getEpoch() == 2 && entry.updateIdentifier.getSequence() == 1) {
                updateCount++;
            } else if (entry.type == LogType.UPDATE_REQ && entry.value == updateValue && entry.secondActor.equals("cohort_2")) {
                updateRequestFound = true;
            }
        }
        assert updateRequestFound : "There should be an update request from " + clientRequest.path().name() + " to cohort_2 with value " + updateValue;
        assertEquals(3, updateCount, "There should be 3 updates with value " + updateValue + " and epoch 2 and sequence 1");
    }
}
