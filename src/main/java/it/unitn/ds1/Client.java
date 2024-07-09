package it.unitn.ds1;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.actor.ActorRef;
import it.unitn.ds1.messages.Message;
import it.unitn.ds1.tools.DotenvLoader;
import it.unitn.ds1.Loggers.ClientLogger;

public class Client extends AbstractActor {
    public ActorRef rxCohort;
    private final ClientLogger logger;

    public Client(ActorRef rxCohort) {
        this.rxCohort = rxCohort;
        this.logger = new ClientLogger(DotenvLoader.getInstance().getLogPath());
    }

    static Props props(ActorRef rxCohort) {
        return Props.create(Client.class, () -> new Client(rxCohort));
    }

    public ActorRef getRxCohort() {
        return rxCohort;
    }

    private void onMessage(Message<?> message) {
        switch (message.topic) {
            case READ:
                assert message.payload instanceof Integer;
                this.logger.logRead(getSelf().path().name(), (Integer) message.payload);
                System.out.println("Received " + message.topic + " message from " + getSender().path().name() + " with value " + message.payload);
                break;
            case WRITEOK:
                System.out.println("Received " + message.topic + " message from " + getSender().path().name()+ " with value " + message.payload);
                break;
            default:
                System.out.println("Received unknown message from " + getSender().path().name());
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
