package it.unitn.ds1;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;

import it.unitn.ds1.messages.SetNeighbors;
import it.unitn.ds1.messages.SetCoordinator;

public class Cohort extends AbstractActor {
    private boolean isCoordinator;
    private ActorRef predecessor;
    private ActorRef successor;
    private ActorRef coordinator;

    static Props props(boolean isCoordinator) {
        return Props.create(Cohort.class, () -> new Cohort(isCoordinator));
    }

    public Cohort(boolean isCoordinator) {
        this.isCoordinator = isCoordinator;
    }

    private void onSetNeighbors(SetNeighbors message) {
        this.predecessor = message.predecessor;
        this.successor = message.successor;
        String role = isCoordinator ? "Coordinator" : "Cohort";
        System.out.println(role + " " + getSelf().path().name() + " neighbors set to: " +
                "predecessor=" + predecessor.path().name() + ", successor=" + successor.path().name());
    }

    private void onSetCoordinator(SetCoordinator message) {
        this.coordinator = message.coordinator;
        String role = isCoordinator ? "Coordinator" : "Cohort";
        System.out.println(role + " " + getSelf().path().name() + " coordinator set to: " + coordinator.path().name());
    }

    // Here we define the mapping between the received message types and our actor methods
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(SetNeighbors.class, this::onSetNeighbors)
                .match(SetCoordinator.class, this::onSetCoordinator)
                .build();
    }
}
