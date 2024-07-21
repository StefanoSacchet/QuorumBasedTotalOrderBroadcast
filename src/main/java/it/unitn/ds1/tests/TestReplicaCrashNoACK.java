package it.unitn.ds1.tests;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import it.unitn.ds1.Client;
import it.unitn.ds1.Cohort;
import it.unitn.ds1.UpdateIdentifier;
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

import static org.junit.jupiter.api.Assertions.*;

public class TestReplicaCrashNoACK {

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

        // make a given cohort crash
        CommunicationWrapper.send(cohorts.get(1), new MessageCommand(MessageTypes.CRASH));
        CommunicationWrapper.send(cohorts.get(3), new MessageCommand(MessageTypes.CRASH));

        threadSleep(1000);

        CommunicationWrapper.send(clients.get(2), new MessageCommand(MessageTypes.TEST_UPDATE));

        threadSleep(3000);
        system.terminate();
    }

    @Test
    void testParseLogFile() throws IOException, InterruptedException {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int expected = 6;
        int crashedCohorts = 2;

        assertEquals(logEntries.size(), expected, "There should be " + expected + " log entries");
        boolean updateRequestFound = false;
        int updateValue = -1;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE_REQ && entry.firstActor.equals("client_2") && entry.secondActor.equals("cohort_2")) {
                updateRequestFound = true;
                updateValue = entry.value;
                break;
            }
        }
        assertEquals(updateValue, 2000000, "Update value should be 2000000");
        assertTrue(updateRequestFound, "Update request should be found");

        UpdateIdentifier check = new UpdateIdentifier(0, 1);
        int updatesDone = 0;
        int N_COHORTS = DotenvLoader.getInstance().getNCohorts() - crashedCohorts; // two are crashed!!!
        //check for N_COHORTS update messages
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.UPDATE && entry.updateIdentifier.equals(check) && updateValue == entry.value) {
                updatesDone++;
            }
        }
        assertEquals(updatesDone, N_COHORTS, "There should be " + N_COHORTS + " update messages");

        int crashDetected = 0;
        for (LogParser.LogEntry entry : logEntries) {
            if (entry.type == LogType.COHORT_DETECTS_COHORT_CRASH && entry.firstActor.equals("cohort_0") && MessageTypes.valueOf(entry.causeOfCrash) == MessageTypes.ACK) {
                crashDetected++;
            }
        }
        assertEquals(crashedCohorts, crashDetected, "Coordinator crash due to no response to update request should be found");
    }
}
