package it.unitn.ds1;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;

import it.unitn.ds1.messages.Message;
import it.unitn.ds1.messages.MessageTypes;
import it.unitn.ds1.tools.DotenvLoader;
import it.unitn.ds1.tools.Loggers.CohortLogger;

import java.util.HashMap;
import java.util.List;

public class Cohort extends AbstractActor {
    private boolean isCoordinator;
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

    static Props props(boolean isCoordinator) {
        return Props.create(Cohort.class, () -> new Cohort(isCoordinator));
    }

    public Cohort(boolean isCoordinator) {
        this.isCoordinator = isCoordinator;
        this.state = 0;
        this.voters = 0;
        this.unstableState = 0;
        this.pendingClient = null;
        this.updateIdentifier = new UpdateIdentifier(0, 0);
        this.history = new HashMap<UpdateIdentifier, Integer>();
        this.logger = new CohortLogger(DotenvLoader.getInstance().getLogPath());
    }

    private void onSetNeighbors(List<ActorRef> cohorts) {
        //1st is predecessor, 2nd is successor
        this.cohorts = cohorts;
//        System.out.println("cohorts are " + this.cohorts);
    }

    private void onSetCoordinator(ActorRef coordinator) {
        this.coordinator = coordinator;
//        String role = isCoordinator ? "Coordinator" : "Cohort";
//        System.out.println(role + " " + getSelf().path().name() + " coordinator set to: " + coordinator.path().name());
    }

    private void onReadRequest(ActorRef sender) {
        sender.tell(new Message<Integer>(MessageTypes.READ, this.state), getSelf());
        this.logger.logReadReq(sender.path().name(), getSelf().path().name());
    }

    private void onUpdateRequest(Integer newState, ActorRef sender) {
        if (this.isCoordinator) {
            this.unstableState = newState;
            this.pendingClient = sender;
            startQuorum(newState);
        } else {
            this.coordinator.tell(new Message<Integer>(MessageTypes.UPDATE_REQUEST, newState), getSelf());
        }
    }

    // Coordinator sends vote requests to all cohorts (included himself)
    private void startQuorum(int newState) {
        for (ActorRef cohort : this.cohorts) {
            cohort.tell(new Message<Integer>(MessageTypes.UPDATE, newState), getSelf());
        }
    }

    // Cohorts receive vote request from coordinator
    private void onUpdate(int newState) {
        ActorRef sender = getSender();
        sender.tell(new Message<Integer>(MessageTypes.ACK, null), getSelf());
    }

    // Coordinator receives votes from cohorts and decide when majority is reached
    private void onACK() {
        this.voters++;
        if (this.voters >= this.cohorts.size() / 2 + 1) {
            for (ActorRef cohort : this.cohorts) {
                cohort.tell(new Message<Integer>(MessageTypes.WRITEOK, this.unstableState), getSelf());
            }
            this.voters = 0;
        }
    }

    // Cohorts receive update confirm from coordinator (included himself)
    // change their state, reset temporary values and increment sequence number
    private void onWriteOk(Integer newState) {
        this.state = newState;
        this.unstableState = 0;
        this.updateIdentifier.setSequence(this.updateIdentifier.getSequence() + 1);
        this.history.put(this.updateIdentifier, this.state);
        this.logger.logUpdate(getSelf().path().name(), this.updateIdentifier.getEpoch(), this.updateIdentifier.getSequence(), this.state);
        if (this.pendingClient != null) {
            this.pendingClient.tell(new Message<Integer>(MessageTypes.WRITEOK, this.state), getSelf());
            this.pendingClient = null;
        }
    }

    private void onMessage(Message<?> message) {
        ActorRef sender = getSender();
        switch (message.topic) {
            case SET_COORDINATOR:
                assert message.payload instanceof ActorRef;
                onSetCoordinator((ActorRef) message.payload);
                break;
            case SET_NEIGHBORS:
                assert message.payload instanceof List<?>;
                onSetNeighbors((List<ActorRef>) message.payload);
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
                onUpdate((Integer) message.payload);
                break;
            case ACK:
                assert message.payload == null;
                if (this.isCoordinator) {
                    onACK();
                } else {
                    Exception e = new Exception("Not a coordinator");
                    e.printStackTrace();
                }
                break;
            case WRITEOK:
                assert message.payload instanceof Integer;
                onWriteOk((Integer) message.payload);
                break;
            default:
                System.out.println("Received message: " + message.topic + " with payload: " + message.payload);
        }
    }

    // Here we define the mapping between the received message types and our actor methods
    @Override
    public Receive createReceive() {
        return receiveBuilder().match(Message.class, this::onMessage).build();
    }
}
