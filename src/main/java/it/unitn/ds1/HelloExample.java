package it.unitn.ds1;

import java.io.IOException;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.AbstractActor;
import akka.actor.Props;

import it.unitn.ds1.messages.SetCoordinator;
import it.unitn.ds1.messages.SetNeighbors;

public class HelloExample {
    final static int N_COHORTS = 5;

    public static void main(String[] args) {
        // Create an actor system named "ringTopologySystem"
        final ActorSystem system = ActorSystem.create("ringTopologySystem");

        // Create an array to hold references to the cohorts
        ActorRef[] cohorts = new ActorRef[N_COHORTS + 1]; // N_COHORTS + 1 to include the coordinator

        // Create the Coordinator cohort
        cohorts[0] = system.actorOf(
                Cohort.props(true), // specifying this cohort as the coordinator
                "cohort_0"       // the new actor name (unique within the system)
        );

        // Create multiple Cohort actors
        for (int i = 1; i <= N_COHORTS; i++) {
            cohorts[i] = system.actorOf(
                    Cohort.props(false), // specifying this cohort as not the coordinator
                    "cohort_" + i         // the new actor name (unique within the system)
            );
        }

        // Link each cohort to its predecessor and successor in a ring
        for (int i = 0; i <= N_COHORTS; i++) {
            ActorRef predecessor = cohorts[(i - 1 + N_COHORTS + 1) % (N_COHORTS + 1)];
            ActorRef successor = cohorts[(i + 1) % (N_COHORTS + 1)];
            cohorts[i].tell(new SetNeighbors(predecessor, successor), ActorRef.noSender());
            cohorts[i].tell(new SetCoordinator(cohorts[0]), ActorRef.noSender());
        }


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
