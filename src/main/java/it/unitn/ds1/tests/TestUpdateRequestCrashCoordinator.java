package it.unitn.ds1.tests;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import it.unitn.ds1.Client;
import it.unitn.ds1.Cohort;
import it.unitn.ds1.loggers.LogParser;
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

public class TestUpdateRequestCrashCoordinator {
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
        CommunicationWrapper.send(clients.get(2), new MessageCommand(MessageTypes.TEST_READ));

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        CommunicationWrapper.send(cohorts.get(0), new MessageCommand(MessageTypes.CRASH));
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        CommunicationWrapper.send(clients.get(2), new MessageCommand(MessageTypes.TEST_UPDATE));

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            system.terminate();
        }
    }

    @Test
    void testParseLogFile() throws IOException, InterruptedException {
        LogParser logParser = new LogParser(DotenvLoader.getInstance().getLogPath());
        List<LogParser.LogEntry> logEntries = logParser.parseLogFile();
        int expected = 6; // 2 for read req and response + N_COHORT - 1 (-1 is coordinator)
        assertEquals(expected, logEntries.size(), "There should be " + expected + " log entries");

    }
}
