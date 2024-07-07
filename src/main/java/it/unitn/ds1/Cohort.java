package it.unitn.ds1;

import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.Props;

import com.typesafe.config.ConfigException;
import it.unitn.ds1.messages.Message;
import it.unitn.ds1.messages.MessageTypes;
import it.unitn.ds1.messages.SetNeighbors;
import it.unitn.ds1.messages.SetCoordinator;
import it.unitn.ds1.tools.Pair;

public class Cohort extends AbstractActor {
    private boolean isCoordinator;
    private ActorRef predecessor;
    private ActorRef successor;
    private ActorRef coordinator;
    private int state;

    static Props props(boolean isCoordinator) {
        return Props.create(Cohort.class, () -> new Cohort(isCoordinator));
    }

    public Cohort(boolean isCoordinator) {
        this.isCoordinator = isCoordinator;
        this.state = 0;
    }

    public int getState() {
        return this.state;
    }

    private void onSetNeighbors(Pair<?, ?> pair) {
        //1st is predecessor, 2nd is successor

        this.predecessor = (ActorRef) pair.getFirst();
        this.successor = (ActorRef) pair.getSecond();
        String role = isCoordinator ? "Coordinator" : "Cohort";
        System.out.println(role + " " + getSelf().path().name() + " neighbors set to: " +
                "predecessor=" + predecessor.path().name() + ", successor=" + successor.path().name());
    }

    private void onSetCoordinator(ActorRef coordinator) {
        this.coordinator = coordinator;
        String role = isCoordinator ? "Coordinator" : "Cohort";
        System.out.println(role + " " + getSelf().path().name() + " coordinator set to: " + coordinator.path().name());
    }

    private void onRead(ActorRef sender) {
        sender.tell(new Message<>(MessageTypes.READOK, this.state), getSelf());
    }

    private void onUpdate(Integer newState, ActorRef sender) {
        this.state = newState;
        sender.tell(new Message<>(MessageTypes.WRITEOK, null), getSelf());
    }

    private void onMessage(Message<?> message) {
        ActorRef sender = getSender();
        switch (message.topic) {
            case SET_COORDINATOR:
                assert message.payload instanceof ActorRef;
                onSetCoordinator((ActorRef) message.payload);
                break;
            case SET_NEIGHBORS:
                assert message.payload instanceof Pair<?, ?>;
                onSetNeighbors((Pair<?, ?>) message.payload);
                break;
            case READ:
                assert message.payload == null;
                onRead(sender);
                break;
            case UPDATE:
                assert message.payload instanceof Integer;
                onUpdate((Integer) message.payload, sender);
                break;
            default:
                System.out.println("Received message: " + message.topic + " with payload: " + message.payload);
        }
    }


    // Here we define the mapping between the received message types and our actor methods
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Message.class, this::onMessage)
                .build();
    }
}
