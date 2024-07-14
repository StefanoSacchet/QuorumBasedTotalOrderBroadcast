package it.unitn.ds1;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;

import it.unitn.ds1.messages.Message;
import it.unitn.ds1.messages.MessageCommand;
import it.unitn.ds1.messages.MessageTimeout;
import it.unitn.ds1.messages.MessageTypes;
import it.unitn.ds1.tools.CommunicationWrapper;
import it.unitn.ds1.tools.DotenvLoader;
import it.unitn.ds1.loggers.CohortLogger;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Cohort extends AbstractActor {
    private boolean isCoordinator;
    private boolean isCrashed;
    private ActorRef predecessor;
    private ActorRef successor;

    private List<ActorRef> cohorts;
    private ActorRef coordinator;

    private int state;
    private int voters;
    private int unstableState;
    private ActorRef pendingClient;

    private final UpdateIdentifier updateIdentifier;
    private HashMap<UpdateIdentifier, Integer> history;

    private final CohortLogger logger;

    // list for coordinator
    private final List<Cancellable> coordinatorHeartbeatTimeouts;
    // timer for cohort for heartbeat
    private Cancellable cohortHeartbeatTimeout;

    // timer for 2phase broadcast, used to detect if someone crashed in the inner network
    private final HashMap<MessageTypes, Cancellable> timersBroadcast;
    // mapping from a message to the expected one, used if a timeout occurs
    private final HashMap<MessageTypes,MessageTypes> sentExpectedMap;

    public static Props props(boolean isCoordinator) {
        return Props.create(Cohort.class, () -> new Cohort(isCoordinator));
    }

    public Cohort(boolean isCoordinator) {
        this.isCoordinator = isCoordinator;
        this.isCrashed = false;
        this.state = 0;
        this.voters = 0;
        this.unstableState = 0;
        this.pendingClient = null;
        this.updateIdentifier = new UpdateIdentifier(0, 0);
        this.history = new HashMap<UpdateIdentifier, Integer>();
        this.logger = new CohortLogger(DotenvLoader.getInstance().getLogPath());
        this.coordinatorHeartbeatTimeouts = new ArrayList<>();
        this.cohortHeartbeatTimeout = null;
        this.timersBroadcast = new HashMap<MessageTypes, Cancellable>();
        this.sentExpectedMap = new HashMap<MessageTypes, MessageTypes>();
        this.sentExpectedMap.put(MessageTypes.UPDATE_REQUEST, MessageTypes.UPDATE);
        this.sentExpectedMap.put(MessageTypes.UPDATE, MessageTypes.ACK);
        this.sentExpectedMap.put(MessageTypes.ACK, MessageTypes.WRITEOK);
        this.sentExpectedMap.put(MessageTypes.HEARTBEAT, null);
    }

    // coordinator sends heartbeat to all cohorts
    private void startHeartbeat() throws InterruptedException {
        for (ActorRef cohort : this.cohorts) {
            if (cohort.path().name().equals(getSelf().path().name()))
                continue;
            Cancellable timer = getContext().system().scheduler().scheduleWithFixedDelay(
                    Duration.create(1, TimeUnit.SECONDS), // when to start generating messages
                    Duration.create(DotenvLoader.getInstance().getHeartbeat(), TimeUnit.MILLISECONDS), // how frequently generate them
                    cohort, // destination actor reference
                    new Message<>(MessageTypes.HEARTBEAT, null), // the message to send
                    getContext().system().dispatcher(), // system dispatcher
                    getSelf() // source of the message (myself)
            );
            this.coordinatorHeartbeatTimeouts.add(timer);
        }
    }

    private void onSetNeighbors(List<ActorRef> cohorts) throws InterruptedException {
        // TODO set predecessor and successor
        // 1st is predecessor, 2nd is successor
        this.cohorts = cohorts;

        if (this.isCoordinator) {
            startHeartbeat();
        }
    }

    private void onSetCoordinator(ActorRef coordinator) {
        this.coordinator = coordinator;
    }

    private void onReadRequest(ActorRef sender) throws InterruptedException {
        CommunicationWrapper.send(sender, new Message<Integer>(MessageTypes.READ, this.state), getSelf());
    }

    private void onUpdateRequest(Integer newState, ActorRef sender) throws InterruptedException {
        if (this.isCoordinator) {
            this.unstableState = newState;
            this.pendingClient = sender;
            startQuorum(newState);
        } else {
            CommunicationWrapper.send(this.coordinator, new Message<Integer>(MessageTypes.UPDATE_REQUEST, newState), getSelf());
            Cancellable timeout = this.setTimeout(MessageTypes.UPDATE_REQUEST, DotenvLoader.getInstance().getTimeout());
            this.timersBroadcast.put(this.sentExpectedMap.get(MessageTypes.UPDATE_REQUEST), timeout);
        }
    }

    // Coordinator sends vote requests to all cohorts (included himself)
    private void startQuorum(int newState) throws InterruptedException {
        for (ActorRef cohort : this.cohorts) {
            CommunicationWrapper.send(cohort, new Message<Integer>(MessageTypes.UPDATE, newState), getSelf());
        }
        // todo set up timers for update -> ACKs
    }

    // Cohorts receive vote request from coordinator
    private void onUpdate(int newState, MessageTypes topic) throws InterruptedException {
        if (this.timersBroadcast.get(topic) != null){
            System.out.println("Received update from coordinator, Canceling timer");
            this.timersBroadcast.get(topic).cancel();
            this.timersBroadcast.remove(topic);
        }
        ActorRef sender = getSender();
        CommunicationWrapper.send(sender, new Message<Integer>(MessageTypes.ACK, null), getSelf());
    }

    // Coordinator receives votes from cohorts and decide when majority is reached
    private void onACK() throws InterruptedException {
        this.voters++;
        if (this.voters >= this.cohorts.size() / 2 + 1) {
            for (ActorRef cohort : this.cohorts) {
                CommunicationWrapper.send(cohort, new Message<Integer>(MessageTypes.WRITEOK, this.unstableState), getSelf());
            }
            this.voters = 0;
        }
    }

    // Cohorts receive update confirm from coordinator (included himself)
    // change their state, reset temporary values and increment sequence number
    private void onWriteOk(Integer newState) throws InterruptedException {
        this.state = newState;
        this.unstableState = 0;
        this.updateIdentifier.setSequence(this.updateIdentifier.getSequence() + 1);
        this.history.put(this.updateIdentifier, this.state);
        this.logger.logUpdate(getSelf().path().name(), this.updateIdentifier.getEpoch(), this.updateIdentifier.getSequence(), this.state);
        if (this.pendingClient != null) {
            CommunicationWrapper.send(this.pendingClient, new Message<Integer>(MessageTypes.WRITEOK, this.state), getSelf());
            this.pendingClient = null;
        }
    }

    private void onRemoveCrashed(ActorRef crashed) {
        this.cohorts.remove(crashed);
        System.out.println(getSelf().path().name() + " removing " + crashed.path().name() + " from cohorts");
        //TODO update ring topology
    }

    private boolean isNeighborListCorrect(List<?> neighbors) {
//        List<?> rawList = (List<?>) neighbors;
        // Check if all elements in the list are instances of ActorRef
        return neighbors.stream().allMatch(element -> element instanceof ActorRef);
    }

    // how to declare generic type in fn signature
    // payload is a generic type

    private Cancellable setTimeout(MessageTypes type, int timeout) {
        return getContext().system().scheduler().scheduleOnce(
                Duration.create(timeout, TimeUnit.MILLISECONDS), // when to start generating messages
                getSelf(), // destination actor reference
                new MessageTimeout<>(type, this.sentExpectedMap.get(type)), // the message to send
                getContext().system().dispatcher(), // system dispatcher
                getSelf() // source of the message (myself)
        );
    }

    private void onHeartbeat(ActorRef sender) {
        System.out.println(getSelf().path().name() + " received heartbeat from " + sender.path().name());
        if (this.cohortHeartbeatTimeout != null) {
            this.cohortHeartbeatTimeout.cancel();
        }
        this.cohortHeartbeatTimeout = setTimeout(MessageTypes.HEARTBEAT, DotenvLoader.getInstance().getHeartbeatTimeout());
    }



    private void onMessage(Message<?> message) throws InterruptedException {
        ActorRef sender = getSender();
        switch (message.topic) {
            case SET_COORDINATOR:
                assert message.payload instanceof ActorRef;
                onSetCoordinator((ActorRef) message.payload);
                break;
            case SET_NEIGHBORS:
                assert message.payload instanceof List<?>;
                if (isNeighborListCorrect((List<?>) message.payload)) {
                    // This cast is safe because we've checked all elements are ActorRef instances
                    @SuppressWarnings("unchecked") // Suppresses unchecked warning for this specific cast
                    List<ActorRef> actorList = (List<ActorRef>) message.payload;
                    onSetNeighbors(actorList);
                } else {
                    throw new InterruptedException("Error: Payload contains non-ActorRef elements.");
                }
                break;
            case READ_REQUEST:
                assert message.payload == null;
                onReadRequest(sender);
                break;
            case UPDATE_REQUEST:
                assert message.payload instanceof Integer;
                onUpdateRequest((Integer) message.payload, sender);
                break;
            case UPDATE:
                assert message.payload instanceof Integer;
                onUpdate((Integer) message.payload, message.topic);
                break;
            case ACK:
                assert message.payload == null;
                if (this.isCoordinator) {
                    onACK();
                } else {
                    throw new InterruptedException("Not a coordinator");
                }
                break;
            case WRITEOK:
                assert message.payload instanceof Integer;
                onWriteOk((Integer) message.payload);
                break;
            case REMOVE_CRASHED:
                assert message.payload instanceof ActorRef;
                onRemoveCrashed((ActorRef) message.payload);
                break;
            case HEARTBEAT:
                onHeartbeat(sender);
                break;
            default:
                System.out.println("Received message: " + message.topic + " with payload: " + message.payload);
        }
    }

    private void onCommandCrash() {
        // if coordinator crashes cancel all heartbeats
        if (this.isCoordinator) {
            for (Cancellable timer : this.coordinatorHeartbeatTimeouts) {
                timer.cancel();
            }
        } else {
            // TODO create method clear all timeouts
            if (this.cohortHeartbeatTimeout != null) {
                this.cohortHeartbeatTimeout.cancel();
            }
        }

        this.isCrashed = true;
        getContext().become(crashed());
    }

    private void onCommandMsg(MessageCommand message) {
        switch (message.topic) {
            case CRASH -> onCommandCrash();
            default -> System.out.println("Received unknown command: " + message.topic);
        }
    }

    private void onHearthbeatTimeout(MessageTypes topic) {
        System.out.println(getSelf().path().name() + " detected " + this.coordinator.path().name() + " crashed, no " + topic);
        this.logger.logCrash(getSelf().path().name(), this.coordinator.path().name(), topic);
        // remove all timers
    }

    private void startLeaderElection(){
        //TODO implement leader election
    }

    private void onUpdateRequestTimeout(MessageTypes topic) {
        MessageTypes cause = this.sentExpectedMap.get(topic);
        System.out.println(getSelf().path().name() + " detected coordinator crashed due to no " + cause);
        this.logger.logCrash(getSelf().path().name(), this.coordinator.path().name(), cause);
        this.startLeaderElection();
    }

    private void onTimeout(MessageTimeout message) {
        switch (message.topic) {
            case HEARTBEAT:
                assert message.payload == null;
                onHearthbeatTimeout(message.topic);
                break;
                case UPDATE_REQUEST:
                    assert this.sentExpectedMap.get(message.topic) != null;
                    assert message.payload == MessageTypes.UPDATE;
                    onUpdateRequestTimeout(message.topic);
                    break;
            default:
                System.out.println("Received unknown timeout: " + message.topic);
            }
            //TODO start leader election and clear all timeouts
    }

    // Here we define the mapping between the received message types and our actor methods
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                //careful! here MessageTimeout is a Message, so we first have to eval this one!
                .match(MessageTimeout.class, this::onTimeout)
                .match(MessageCommand.class, this::onCommandMsg)
                .match(Message.class, this::onMessage)
                .build();
    }

    final AbstractActor.Receive crashed() {
        return receiveBuilder()
                .matchAny(msg -> {
                })
//                .matchAny(msg -> {
//                    System.out.println("deferring message"+ msg.toString());
//                })
                .build();
    }
}
