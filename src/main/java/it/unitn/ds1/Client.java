package it.unitn.ds1;

import akka.actor.AbstractActor;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.ActorRef;
import it.unitn.ds1.messages.Message;
import it.unitn.ds1.messages.MessageTest;
import it.unitn.ds1.messages.MessageTypes;
import it.unitn.ds1.tools.CommunicationWrapper;
import it.unitn.ds1.tools.DotenvLoader;
import it.unitn.ds1.loggers.ClientLogger;
import scala.concurrent.duration.Duration;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class Client extends AbstractActor {
    public ActorRef rxCohort;
    private final ClientLogger logger;

    public Client(ActorRef rxCohort) {
        this.rxCohort = rxCohort;
        this.logger = new ClientLogger(DotenvLoader.getInstance().getLogPath());
    }

    public static Props props(ActorRef rxCohort) {
        return Props.create(Client.class, () -> new Client(rxCohort));
    }

    public ActorRef getRxCohort() {
        return rxCohort;
    }

    private void onMessage(Message<?> message) {
        System.out.println("received a message " + message.topic);
        switch (message.topic) {
            case READ:
                assert message.payload instanceof Integer;
                this.logger.logRead(getSelf().path().name(), (Integer) message.payload);
                System.out.println("Received " + message.topic + " message from " + getSender().path().name() + " with value " + message.payload);
                break;
            case WRITEOK:
                System.out.println("Received " + message.topic + " message from " + getSender().path().name() + " with value " + message.payload);
                break;
            default:
                System.out.println("Received unknown message from " + getSender().path().name());
        }
    }

    private void onTestMessages(MessageTest msg) throws InterruptedException {
        switch(msg.topic){
            case TEST_READ:
                Message<Object> sendMsg = new Message<>(MessageTypes.READ_REQUEST, null);
                CommunicationWrapper.send(rxCohort, sendMsg, getSelf());
                System.out.println();
                break;
            case TEST_UPDATE:
                System.out.println("Test update request");
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
                .match(MessageTest.class, this::onTestMessages)
                .build();
    }
}
