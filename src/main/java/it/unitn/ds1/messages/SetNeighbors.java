package it.unitn.ds1.messages;

import akka.actor.ActorRef;

// Message class to set neighbors
public class SetNeighbors {
    public final ActorRef predecessor;
    public final ActorRef successor;

    public SetNeighbors(ActorRef predecessor, ActorRef successor) {
        this.predecessor = predecessor;
        this.successor = successor;
    }
}
