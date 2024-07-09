package it.unitn.ds1;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;


import it.unitn.ds1.messages.MessageTypes;
import it.unitn.ds1.messages.Message;
import it.unitn.ds1.tools.DotenvLoader;
import it.unitn.ds1.tools.Loggers.Logger;

public class Main {


    public static void main(String[] args) {
        DotenvLoader dotenv = DotenvLoader.getInstance();
        Logger.clearFile(dotenv.getLogPath());
        int N_COHORTS = dotenv.getNCohorts();


        // Create an actor system named "ringTopologySystem"
        final ActorSystem system = ActorSystem.create("ringTopologySystem");

        // Create an array to hold references to the cohorts

        List<ActorRef> cohorts = new ArrayList<ActorRef>(N_COHORTS + 1);

        // Create the Coordinator cohort
        ActorRef coordinator = system.actorOf(
                Cohort.props(true), // specifying this cohort as the coordinator
                "cohort_0"       // the new actor name (unique within the system)
        );
        cohorts.add(coordinator);

        // Create multiple Cohort actors
        for (int i = 1; i <= N_COHORTS; i++) {
            ActorRef cohort = system.actorOf(
                    Cohort.props(false), // specifying this cohort as not the coordinator
                    "cohort_" + i
            );
            cohorts.add(cohort);
        }

        // Link all cohorts with each other
        for (ActorRef cohort : cohorts) {
            cohort.tell(new Message<List<ActorRef>>(MessageTypes.SET_NEIGHBORS, cohorts), ActorRef.noSender());
            cohort.tell(new Message<ActorRef>(MessageTypes.SET_COORDINATOR, cohorts.get(0)), ActorRef.noSender());
        }

        List<ActorRef> clients = new ArrayList<ActorRef>(N_COHORTS + 1);
        for (int i = 0; i <= N_COHORTS; i++) {
            ActorRef client = system.actorOf(
                    Client.props(cohorts.get(i)),
                    "client_" + i
            );
            clients.add(client);
        }

        Message<Object> msg1 = new Message<Object>(MessageTypes.UPDATE_REQUEST, 2000000);
        cohorts.get(0).tell(msg1, clients.get(0));

        try {
            System.in.read();
        } catch (IOException ioe) {
        } finally {
            system.terminate();
        }

        Message<Object> msg2 = new Message<Object>(MessageTypes.READ_REQUEST, null);
        cohorts.get(0).tell(msg2, clients.get(0));

//        System.out.println("Current java version is " + System.getProperty("java.version"));
//        System.out.println(">>> Press ENTER to exit <<<");
//        try {
//            System.in.read();
//        } catch (IOException ioe) {
//        } finally {
//            system.terminate();
//        }
    }
}
