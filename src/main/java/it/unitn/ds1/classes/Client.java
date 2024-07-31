package it.unitn.ds1.classes;

import akka.actor.AbstractActor;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.ActorRef;
import it.unitn.ds1.messages.Message;
import it.unitn.ds1.messages.MessageCommand;
import it.unitn.ds1.messages.MessageTypes;
import it.unitn.ds1.tools.CommunicationWrapper;
import it.unitn.ds1.tools.DotenvLoader;
import it.unitn.ds1.loggers.ClientLogger;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Client extends AbstractActor {
    public ActorRef rxCohort;
    private final ClientLogger logger;
    List<Cancellable> pendingTimeouts;

    public Client(ActorRef rxCohort) throws InterruptedException {
        this.rxCohort = rxCohort;
        this.logger = new ClientLogger(DotenvLoader.getInstance().getLogPath());
        this.pendingTimeouts = new ArrayList<>();
        CommunicationWrapper.send(this.rxCohort, new MessageCommand(MessageTypes.CLIENT_BINDING), getSelf());
    }

    public static Props props(ActorRef rxCohort) {
        return Props.create(Client.class, () -> new Client(rxCohort));
    }

    private Cancellable timeout(ActorRef rxCohort) {
        return getContext().system().scheduler().scheduleOnce(
                Duration.create(DotenvLoader.getInstance().getTimeout(), TimeUnit.MILLISECONDS),
                getSelf(),
                new Message<>(MessageTypes.TIMEOUT, rxCohort), // the message to send
                getContext().system().dispatcher(), getSelf()
        );
    }

    // When the client receives a read response it logs the response and cancels the timer
    private void onReadRes(int state) {
        assert !this.pendingTimeouts.isEmpty();

        Cancellable timeout = this.pendingTimeouts.get(0);
        timeout.cancel();
        this.pendingTimeouts.remove(timeout);
        this.logger.logRead(getSelf().path().name(), state);
        System.out.println(getSelf().path().name() + " Timeout cancelled");
        System.out.println("Received READ message from " + getSender().path().name() + " with value " + state);
    }

    private void onCrashDetect(ActorRef crashedCohort) {
        assert !this.pendingTimeouts.isEmpty();
        this.pendingTimeouts.remove(0);
        this.logger.logCrash(getSelf().path().name(), crashedCohort.path().name());
        System.out.println(getSelf().path().name() + " detected " + crashedCohort.path().name() + " Crashed");
    }

    private void onMessage(Message<?> message) {
        switch (message.topic) {
            case READ:
                assert message.payload instanceof Integer;
                onReadRes((Integer) message.payload);
                break;
            case WRITEOK:
                System.out.println("Received " + message.topic + " message from " + getSender().path().name() + " with value " + message.payload);
                break;
            case TIMEOUT:
                assert message.payload instanceof ActorRef;
                onCrashDetect((ActorRef) message.payload);
                break;
            default:
                System.out.println(getSelf().path().name() + " Received unknown message: " + message.topic + " from " + getSender().path().name());
        }
    }

    // When the client sends a read request it starts a timer to wait for the response
    private void onSendReadRequest() throws InterruptedException {
        this.pendingTimeouts.add(timeout(this.rxCohort));
        this.logger.logReadReq(getSelf().path().name(), this.rxCohort.path().name());
        Message<Object> sendMsg = new Message<>(MessageTypes.READ_REQUEST, null);
        CommunicationWrapper.send(this.rxCohort, sendMsg, getSelf());
    }

    // the client does not expect a response from the update request
    private void onSendUpdateRequest() throws InterruptedException {
        int value = Integer.parseInt(getSelf().path().name().split("_")[1]) * 1_000_000;
        this.logger.logUpdateReq(getSelf().path().name(), this.rxCohort.path().name(), value);
        Message<Object> sendMsg = new Message<>(MessageTypes.UPDATE_REQUEST, value);
        CommunicationWrapper.send(this.rxCohort, sendMsg, getSelf());
    }

    private void onMessageCommand(MessageCommand msg) throws InterruptedException {
        switch (msg.topic) {
            case TEST_READ:
                onSendReadRequest();
                break;
            case TEST_UPDATE:
                System.out.println("Test update request");
                onSendUpdateRequest();
                break;
            default:
                System.out.println(getSelf().path().name() + " Received unknown message from " + getSender().path().name());
        }
    }

    // Here we define the mapping between the received message types and our actor methods
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(MessageCommand.class, this::onMessageCommand)
                .match(Message.class, this::onMessage)
                .build();
    }
}
