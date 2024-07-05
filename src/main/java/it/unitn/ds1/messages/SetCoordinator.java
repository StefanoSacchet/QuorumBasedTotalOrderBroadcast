package it.unitn.ds1.messages;

import akka.actor.ActorRef;

public class SetCoordinator {
    public final ActorRef coordinator;

    public SetCoordinator(ActorRef coordinator) {
        this.coordinator = coordinator;
    }
}
