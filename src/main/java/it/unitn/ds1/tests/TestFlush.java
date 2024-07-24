package it.unitn.ds1.tests;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import it.unitn.ds1.Client;
import it.unitn.ds1.Cohort;
import it.unitn.ds1.loggers.LogParser;
import it.unitn.ds1.loggers.LogType;
import it.unitn.ds1.loggers.Logger;
import it.unitn.ds1.messages.Message;
import it.unitn.ds1.messages.MessageCommand;
import it.unitn.ds1.messages.MessageTypes;
import it.unitn.ds1.tools.CommunicationWrapper;
import it.unitn.ds1.tools.DotenvLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFlush {

    private static void threadSleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeAll
    static void setUp() throws IOException, InterruptedException {
        DotenvLoader dotenv = DotenvLoader.getInstance();
        Logger.clearFile(dotenv.getLogPath());
        int N_COHORTS = dotenv.getNCohorts();


        // Create an actor system named "ringTopologySystem"
        final ActorSystem system = ActorSystem.create("ringTopologySystem");

        // Create an array to hold references to the cohorts

        List<ActorRef> cohorts = new ArrayList<ActorRef>(N_COHORTS);

        // Create the Coordinator cohort
        ActorRef coordinator = system.actorOf(Cohort.props(true), // specifying this cohort as the coordinator
                "cohort_0"       // the new actor name (unique within the system)
        );
        cohorts.add(coordinator);

        // Create multiple Cohort actors
        for (int i = 1; i < N_COHORTS; i++) {
            ActorRef cohort = system.actorOf(Cohort.props(false), // specifying this cohort as not the coordinator
                    "cohort_" + i);
            cohorts.add(cohort);
        }

        // Link all cohorts with each other
        for (ActorRef cohort : cohorts) {
            List<ActorRef> copyCohorts = new ArrayList<>(cohorts);
            CommunicationWrapper.send(cohort, new Message<List<ActorRef>>(MessageTypes.SET_NEIGHBORS, copyCohorts), ActorRef.noSender());
            CommunicationWrapper.send(cohort, new Message<ActorRef>(MessageTypes.SET_COORDINATOR, cohorts.get(0)), ActorRef.noSender());
        }

        List<ActorRef> clients = new ArrayList<ActorRef>(N_COHORTS);
        for (int i = 0; i < N_COHORTS; i++) {
            ActorRef client = system.actorOf(Client.props(cohorts.get(i)), "client_" + i);
            clients.add(client);
        }

        threadSleep(1000);

        // make a given cohort crash
        CommunicationWrapper.send(cohorts.get(0), new MessageCommand(MessageTypes.CRASH_ONLY_ONE_WRITEOK));

        threadSleep(1000);

        CommunicationWrapper.send(clients.get(1), new MessageCommand(MessageTypes.TEST_UPDATE));

        threadSleep(4000);
        system.terminate();
    }

    @Test
    void testParseLogFile() {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int expected = 15; // 1 client read_req + 2 replicas update done + 3 replicas no writeOk + 3 start leader election + 1 replica no heartbeat + 1 replica start election + 1 leader found + 3 flushes
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
            if (entry.type == LogType.COHORT_DETECTS_COHORT_CRASH && (MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.WRITEOK || MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.HEARTBEAT)) {
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
    }
}
