package it.unitn.ds1;

import java.util.List;
import java.util.ArrayList;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;


import it.unitn.ds1.messages.MessageCommand;
import it.unitn.ds1.messages.MessageTypes;
import it.unitn.ds1.messages.Message;
import it.unitn.ds1.tools.CommunicationWrapper;
import it.unitn.ds1.tools.DotenvLoader;
import it.unitn.ds1.loggers.Logger;

public class Main {

    private static void threadSleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws InterruptedException {
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

        threadSleep(2500);

        CommunicationWrapper.send(clients.get(1), new MessageCommand(MessageTypes.TEST_UPDATE));

        threadSleep(1000);

        CommunicationWrapper.send(clients.get(1), new MessageCommand(MessageTypes.TEST_READ));

//        try {
//            Thread.sleep(2000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//
//        CommunicationWrapper.send(clients.get(1), new MessageCommand(MessageTypes.TEST_UPDATE));

        // tell all cohorts to remove the crashed one
//        for (ActorRef cohort : cohorts) {
//            CommunicationWrapper.send(cohort, new Message<>(MessageTypes.REMOVE_CRASHED, cohorts.get(2)), cohorts.get(1));
//        }
//
//        Message<Object> msg1 = new Message<Object>(MessageTypes.UPDATE_REQUEST, 2000000);
//        CommunicationWrapper.send(cohorts.get(0), msg1, clients.get(0));
//        System.out.println("sent update request");
//
//        try {
//            Thread.sleep(1000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//
//        Message<Object> msg2 = new Message<Object>(MessageTypes.READ_REQUEST, null);
//        CommunicationWrapper.send(cohorts.get(0), msg2, clients.get(0));

        threadSleep(3000);
        system.terminate();
    }
}
