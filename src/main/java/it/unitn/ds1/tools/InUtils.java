package it.unitn.ds1.tools;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import it.unitn.ds1.classes.Client;
import it.unitn.ds1.classes.Cohort;
import it.unitn.ds1.loggers.Logger;
import it.unitn.ds1.messages.Message;
import it.unitn.ds1.messages.MessageTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

public class InUtils {

    public ActorSystem system;
    public List<ActorRef> clients;
    public List<ActorRef> cohorts;

    public InUtils() throws InterruptedException {
        DotenvLoader dotenv = DotenvLoader.getInstance();
        Logger.clearFile(dotenv.getLogPath());
        int N_COHORTS = dotenv.getNCohorts();

        // Create an actor system named "ringTopologySystem"
        final ActorSystem system = ActorSystem.create("ringTopologySystem");

        // Create an array to hold references to the cohorts

        List<ActorRef> cohorts = new ArrayList<>(N_COHORTS);

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

        // Use a CountDownLatch to wait until all messages are sent
        CountDownLatch latch = new CountDownLatch(cohorts.size());

        // Link all cohorts with each other
        for (ActorRef cohort : cohorts) {
            List<ActorRef> copyCohorts = new ArrayList<>(cohorts);
            CompletableFuture.runAsync(() -> {
                try {
                    CommunicationWrapper.send(cohort, new Message<>(MessageTypes.SET_NEIGHBORS, copyCohorts), ActorRef.noSender());
                    CommunicationWrapper.send(cohort, new Message<>(MessageTypes.SET_COORDINATOR, cohorts.get(0)), ActorRef.noSender());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                latch.countDown();
            });
        }

        // Wait for all messages to be sent
        latch.await();

        List<ActorRef> clients = new ArrayList<>(N_COHORTS);
        for (int i = 0; i < N_COHORTS; i++) {
            ActorRef client = system.actorOf(Client.props(cohorts.get(i)), "client_" + i);
            clients.add(client);
        }

        this.system = system;
        this.clients = clients;
        this.cohorts = cohorts;
    }

    public static void threadSleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
