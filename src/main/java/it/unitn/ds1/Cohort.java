package it.unitn.ds1;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;

import it.unitn.ds1.messages.*;
import it.unitn.ds1.tools.CommunicationWrapper;
import it.unitn.ds1.tools.DotenvLoader;
import it.unitn.ds1.loggers.CohortLogger;
import it.unitn.ds1.tools.InstanceController;
import it.unitn.ds1.tools.Pair;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class Cohort extends AbstractActor {
    private boolean isCoordinator;
    private boolean isCrashed;
    private ActorRef predecessor;
    private ActorRef successor;

    private List<ActorRef> cohorts;
    private ActorRef coordinator;

    private int state;

    private final HashMap<UpdateIdentifier, Integer> votersState;
    private final HashMap<UpdateIdentifier, Integer> unstableStateMap;

    private final UpdateIdentifier updateIdentifier;
    private final HashMap<UpdateIdentifier, Integer> history;

    private final CohortLogger logger;

    // list for coordinator
    private final List<Cancellable> coordinatorHeartbeatTimeouts;
    // timer for cohort for heartbeat
    private Cancellable cohortHeartbeatTimeout;

    // timer for 2phase broadcast, used to detect if someone crashed in the inner network
    private HashMap<MessageTypes, List<Cancellable>> timersBroadcast;
    // mapping from a message to the expected one, used if a timeout occurs
    private final HashMap<MessageTypes, MessageTypes> sentExpectedMap;

    //The coordinator needs to keep track of timersBroadcast for each cohort
    private HashMap<ActorRef, HashMap<MessageTypes, List<Cancellable>>> timersBroadcastCohorts;

    private boolean noWriteOkResponse;
    private boolean onlyOneWriteOkRes;

    public static Props props(boolean isCoordinator) {
        return Props.create(Cohort.class, () -> new Cohort(isCoordinator));
    }

    public Cohort(boolean isCoordinator) {
        this.isCoordinator = isCoordinator;
        this.isCrashed = false;
        this.state = 0;
        this.votersState = new HashMap<>();
        this.unstableStateMap = new HashMap<>();
        this.updateIdentifier = new UpdateIdentifier(0, 0);
        this.history = new HashMap<>();
        this.logger = new CohortLogger(DotenvLoader.getInstance().getLogPath());
        this.coordinatorHeartbeatTimeouts = new ArrayList<>();
        this.cohortHeartbeatTimeout = null;
        this.timersBroadcast = setTimersBroadcast();

        // each cohort has a map of all timers that he has pending
        if (!isCoordinator) {
            this.timersBroadcastCohorts = null;
        } else {
            this.timersBroadcastCohorts = new HashMap<>();
        }

        // map to quickly understand sequence of messages in the 2phase broadcast
        this.sentExpectedMap = new HashMap<>();
        this.sentExpectedMap.put(MessageTypes.UPDATE_REQUEST, MessageTypes.UPDATE);
        this.sentExpectedMap.put(MessageTypes.UPDATE, MessageTypes.ACK);
        this.sentExpectedMap.put(MessageTypes.ACK, MessageTypes.WRITEOK);
        this.sentExpectedMap.put(MessageTypes.HEARTBEAT, null);
        this.sentExpectedMap.put(MessageTypes.ELECTION, MessageTypes.ACK);

        // variable used to test the case where the coordinator crashes before sending write_ok
        this.noWriteOkResponse = false;
        this.onlyOneWriteOkRes = false;
    }

    private HashMap<MessageTypes, List<Cancellable>> setTimersBroadcast() {
        HashMap<MessageTypes, List<Cancellable>> timersBroadcast = new HashMap<>();
        timersBroadcast.put(MessageTypes.UPDATE_REQUEST, new ArrayList<>());
        timersBroadcast.put(MessageTypes.UPDATE, new ArrayList<>());
        timersBroadcast.put(MessageTypes.ACK, new ArrayList<>());
        timersBroadcast.put(MessageTypes.WRITEOK, new ArrayList<>());
        return timersBroadcast;
    }

    // coordinator sends heartbeat to all cohorts
    private void startHeartbeat() {
        for (ActorRef cohort : this.cohorts) {
            if (cohort.path().name().equals(getSelf().path().name())) {
                continue;
            }
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

    private void initTimersBroadcastCohorts(List<ActorRef> cohorts) {
        for (ActorRef cohort : cohorts) {
            this.timersBroadcastCohorts.put(cohort, setTimersBroadcast());
        }
    }

    // convenience method to set a timeout for a message
    private Cancellable setTimeout(MessageTypes type, int timeout, ActorRef sender) {
        return getContext().system().scheduler().scheduleOnce(
                Duration.create(timeout, TimeUnit.MILLISECONDS), // when to start generating messages
                getSelf(), // destination actor reference
                new MessageTimeout<>(type, this.sentExpectedMap.get(type)), // the message to send
                getContext().system().dispatcher(), // system dispatcher
                sender // source of the message (myself)
        );
    }

    private void updatePredecessorSuccessor(List<ActorRef> cohorts) {
        int N_COHORTS = cohorts.size();
        String myName = getSelf().path().name();
        int myIndex = -1;

        // Find the current index in the updated list
        for (int i = 0; i < N_COHORTS; i++) {
            if (cohorts.get(i).path().name().equals(myName)) {
                myIndex = i;
                break;
            }
        }
        if (myIndex == -1) {
            throw new IllegalStateException("Cohort not found in the list");
        }

        this.predecessor = cohorts.get((myIndex - 1 + N_COHORTS) % N_COHORTS);
        this.successor = cohorts.get((myIndex + 1) % N_COHORTS);
    }

    // TODO it is useful??
    private void onRemoveCrashed(ActorRef crashed) {
        this.cohorts.remove(crashed);
        System.out.println(getSelf().path().name() + " removing " + crashed.path().name() + " from cohorts");
        //TODO update ring topology
    }


    /************************************************************************
     *
     *  NORMAL MESSAGE HANDLERS
     *
     ************************************************************************/


    private void onSetNeighbors(List<ActorRef> cohorts) {
        this.cohorts = cohorts;
        this.updatePredecessorSuccessor(cohorts);

        if (this.isCoordinator && this.coordinatorHeartbeatTimeouts.isEmpty()) {
            initTimersBroadcastCohorts(cohorts);
            startHeartbeat();
        }
    }

    private void onSetCoordinator(ActorRef coordinator) {
        this.coordinator = coordinator;
    }

    private void onReadRequest(ActorRef sender) throws InterruptedException {
        CommunicationWrapper.send(sender, new Message<>(MessageTypes.READ, this.state), getSelf());
    }

    private void onUpdateRequest(Integer newState, ActorRef sender) throws InterruptedException {
        if (this.isCoordinator) {
            startQuorum(newState);
        } else {
            CommunicationWrapper.send(this.coordinator, new Message<>(MessageTypes.UPDATE_REQUEST, newState), getSelf());
            // same as heartbeat, only coordinator can send UPDATE response
            Cancellable timeout = this.setTimeout(MessageTypes.UPDATE_REQUEST, DotenvLoader.getInstance().getTimeout(), this.coordinator);
            MessageTypes key = this.sentExpectedMap.get(MessageTypes.UPDATE_REQUEST);
            List<Cancellable> timersList = this.timersBroadcast.get(key);
            timersList.add(timeout);
        }
    }

    // Coordinator sends vote requests to all cohorts (included himself)
    private void startQuorum(int newState) throws InterruptedException {
        this.updateIdentifier.setSequence(this.updateIdentifier.getSequence() + 1);
        this.unstableStateMap.put(this.updateIdentifier.copy(), newState);

        for (ActorRef cohort : this.cohorts) {
            HashMap<MessageTypes, List<Cancellable>> timersCohort = this.timersBroadcastCohorts.get(cohort);
            // we are adding cohort because we want to be able who crashed if we did not receive the ack message
            Cancellable timeout = setTimeout(MessageTypes.UPDATE, DotenvLoader.getInstance().getTimeout(), cohort);
            MessageTypes key = this.sentExpectedMap.get(MessageTypes.UPDATE);
            List<Cancellable> timersList = timersCohort.get(key);
            timersList.add(timeout);
            Pair<UpdateIdentifier, Integer> payload = new Pair<>(this.updateIdentifier.copy(), newState);
            CommunicationWrapper.send(cohort, new Message<>(MessageTypes.UPDATE, payload), getSelf());
        }
    }

    // Cohorts receive vote request from coordinator
    private void onUpdate(UpdateIdentifier updateID, int newState, MessageTypes topic) throws InterruptedException {
        // remove the timer for the update (only the cohort that sent update_request has it)
        List<Cancellable> timersList = this.timersBroadcast.get(topic);
        if (!timersList.isEmpty()) {
            Cancellable timer = timersList.remove(0);
            timer.cancel();
        }

        // start the timer for the ack
        if (!this.isCoordinator) {
            Cancellable timeout = setTimeout(MessageTypes.ACK, DotenvLoader.getInstance().getTimeout(), this.coordinator);
            MessageTypes key = this.sentExpectedMap.get(MessageTypes.ACK);
            List<Cancellable> newList = this.timersBroadcast.get(key);
            newList.add(timeout);
        }

        CommunicationWrapper.send(this.coordinator, new Message<>(MessageTypes.ACK, updateID), getSelf());
    }

    // Coordinator receives votes from cohorts and decide when majority is reached
    private void onACK(ActorRef sender, UpdateIdentifier updateID, MessageTypes topic) throws InterruptedException {
        HashMap<MessageTypes, List<Cancellable>> timersCohort = this.timersBroadcastCohorts.get(sender);
        List<Cancellable> timersList = timersCohort.get(MessageTypes.ACK);
        Cancellable timer = timersList.remove(0);
        timer.cancel();

        // we have to make the coordinator crash to test this functionality
        if (this.noWriteOkResponse) {
            this.isCrashed = true;
            getContext().become(crashed());
            return;
        }

        this.votersState.merge(updateID, 1, Integer::sum);

        int voters = this.votersState.get(updateID);
        if (voters >= this.cohorts.size() / 2 + 1) {
            int newState = this.unstableStateMap.get(updateID);

            for (ActorRef cohort : this.cohorts) {
                if (cohort.equals(getSelf())) {
                    this.state = newState;
                    this.logger.logUpdate(getSelf().path().name(), updateID.getEpoch(), updateID.getSequence(), newState);
                    continue;
                }
                Pair<UpdateIdentifier, Integer> payload = new Pair<>(updateID, newState);
                CommunicationWrapper.send(cohort, new Message<>(MessageTypes.WRITEOK, payload), getSelf());

                // if we want to test the case where only a part of cohorts get the write_ok
                if (this.onlyOneWriteOkRes && cohort.path().name().equals(this.cohorts.get(1).path().name())) {
                    System.out.println("Crashing cohort " + getSelf().path().name());
                    CommunicationWrapper.send(getSelf(), new MessageCommand(MessageTypes.CRASH));
                    break;
                }
            }
            this.unstableStateMap.remove(updateID);
            this.votersState.put(updateID, 0);
        }
    }

    // Cohorts receive update confirm from coordinator (not included himself)
    // change their state, reset temporary values and increment sequence number
    private void onWriteOk(UpdateIdentifier updateID, Integer newState) {
        // remove pending timer for this message
        List<Cancellable> timersList = this.timersBroadcast.get(MessageTypes.WRITEOK);
        assert !timersList.isEmpty();
        Cancellable timer = timersList.remove(0);
        timer.cancel();

        this.state = newState;
        this.updateIdentifier.setSequence(updateID.getSequence());
        this.history.put(this.updateIdentifier, this.state);
        this.logger.logUpdate(getSelf().path().name(), this.updateIdentifier.getEpoch(), this.updateIdentifier.getSequence(), this.state);
    }

    private void onHeartbeat(ActorRef sender) {
        assert sender.equals(this.coordinator);
        // we have received a heartbeat from the coordinator
        if (this.cohortHeartbeatTimeout != null) {
            this.cohortHeartbeatTimeout.cancel();
        }
        // we have to reset the timer for the heartbeat
        // we use coordinator as sender because only him can send heartbeats
        this.cohortHeartbeatTimeout = setTimeout(MessageTypes.HEARTBEAT, DotenvLoader.getInstance().getHeartbeatTimeout(), this.coordinator);
    }


    /************************************************************************
     *
     *  LEADER ELECTION HANDLERS
     *
     ************************************************************************/


    // This method is called when the cohort receives a START_ELECTION message
    // It's received when a cohort detects that the coordinator has crashed from UPDATE_REQUEST timeout
    private void onStartElection(MessageTypes topic, List<ActorRef> cohorts) throws InterruptedException {
        this.onSetNeighbors(cohorts);
        startLeaderElection(topic);
    }

    private void startLeaderElection(MessageTypes cause) throws InterruptedException {
        this.cancelAllTimeouts();
        this.logger.logLeaderElectionStart(getSelf().path().name(), this.coordinator.path().name());
        getContext().become(leader_election());

        try {
            Thread.sleep(DotenvLoader.getInstance().getRTT());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // now we all are in leader election mode and we are ready to perform it
        HashMap<ActorRef, UpdateIdentifier> payload = new HashMap<>();
        payload.put(getSelf(), this.updateIdentifier);
        CommunicationWrapper.send(this.successor, new MessageElection<>(MessageTypes.ELECTION, payload), getSelf());
        Cancellable timeout = setTimeout(MessageTypes.ELECTION, DotenvLoader.getInstance().getTimeout(), this.successor);
        MessageTypes key = this.sentExpectedMap.get(MessageTypes.ELECTION);
        this.timersBroadcast.get(key).add(timeout);
    }

    // get the messages that have to be flushed given the last update for a cohort
    private HashMap<UpdateIdentifier, Integer> getFlush(UpdateIdentifier lastUpdate) {
        HashMap<UpdateIdentifier, Integer> payload = new HashMap<>();
        for (Map.Entry<UpdateIdentifier, Integer> entry : this.history.entrySet()) {
            UpdateIdentifier key = entry.getKey();
            Integer value = entry.getValue();
            if (key.compareTo(lastUpdate) > 0) {
                payload.put(key, value);
            }
        }
        return payload;
    }

    // choose the new leader given the map of cohorts
    public ActorRef chooseNewLeader(HashMap<ActorRef, UpdateIdentifier> map) {
        ActorRef newLeader = null;
        int bestFirstValue = Integer.MIN_VALUE;
        int bestSecondValue = Integer.MIN_VALUE;

        for (Map.Entry<ActorRef, UpdateIdentifier> entry : map.entrySet()) {
            ActorRef key = entry.getKey();
            UpdateIdentifier value = entry.getValue();
            int firstValue = value.getEpoch();
            int secondValue = value.getSequence();

            if (firstValue > bestFirstValue ||
                    (firstValue == bestFirstValue && secondValue > bestSecondValue) ||
                    (firstValue == bestFirstValue && secondValue == bestSecondValue && key.compareTo(Objects.requireNonNull(newLeader)) > 0)) {
                newLeader = key;
                bestFirstValue = firstValue;
                bestSecondValue = secondValue;
            }
        }

        return newLeader;
    }

    // Here we have received a message from predecessor, I have to add me and forward
    private void onElection(ActorRef sender, HashMap<ActorRef, UpdateIdentifier> map) throws InterruptedException {
        CommunicationWrapper.send(sender, new MessageElection<>(MessageTypes.ACK, null), getSelf());
        if (map.containsKey(getSelf())) {
            // I am contained in the map, which means the leader election is finished, we have to find the new coordinator
            ActorRef newLeader = chooseNewLeader(map);

            if (newLeader.equals(getSelf())) {
                // I am the new coordinator
                this.isCoordinator = true;
                this.timersBroadcastCohorts = new HashMap<>();
                System.out.println(getSelf().path().name() + " is the new coordinator");
                this.logger.logLeaderFound(getSelf().path().name());

                for (ActorRef cohort : this.cohorts) {
                    HashMap<UpdateIdentifier, Integer> payload = getFlush(map.get(cohort));
                    CommunicationWrapper.send(cohort, new MessageElection<>(MessageTypes.SYNC, payload), getSelf());
                }
            }
        } else {
            map.put(getSelf(), this.updateIdentifier);
            CommunicationWrapper.send(this.successor, new MessageElection<>(MessageTypes.ELECTION, map), getSelf());
            Cancellable timeout = setTimeout(MessageTypes.ELECTION, DotenvLoader.getInstance().getTimeout(), this.successor);
            MessageTypes key = this.sentExpectedMap.get(MessageTypes.ELECTION);
            this.timersBroadcast.get(key).add(timeout);
        }
    }

    private void onACKElectionMode(ActorRef sender) {
        // I have received the ack, so I have to remove the timeout
        List<Cancellable> timeoutList = this.timersBroadcast.get(MessageTypes.ACK);
        assert !timeoutList.isEmpty();
        Cancellable timer = timeoutList.remove(0);
        timer.cancel();
    }

    private void onSync(ActorRef sender, HashMap<UpdateIdentifier, Integer> flushedUpdates) {
        this.cancelAllTimeouts();
        this.timersBroadcast = setTimersBroadcast();
        getContext().become(createReceive());
        this.coordinator = sender;

        // I have to update my history with the flushed updates
        System.out.println(getSelf().path().name() + " received sync");
        System.out.println("Flushing updates: " + flushedUpdates);

        this.history.putAll(flushedUpdates);
        // search the last update in the flushed updates and set the state
        int oldState = this.state;
        for (Map.Entry<UpdateIdentifier, Integer> entry : flushedUpdates.entrySet()) {
            UpdateIdentifier key = entry.getKey();
            Integer value = entry.getValue();
            if (key.compareTo(this.updateIdentifier) > 0) {
                this.state = value;
            }
        }
        this.updateIdentifier.increaseEpoch();

        if (!flushedUpdates.isEmpty()) {
            this.logger.logFlush(getSelf().path().name(), oldState, this.state);
        }

        if (this.isCoordinator) {
            this.initTimersBroadcastCohorts(this.cohorts);
            this.startHeartbeat();
        }
    }


    /************************************************************************
     *
     *  TIMEOUT HANDLERS
     *
     ************************************************************************/


    private void cancelAllTimeouts() {
        if (this.cohortHeartbeatTimeout != null) {
            this.cohortHeartbeatTimeout.cancel();
        }
        // cancel all remaining timeout
        for (List<Cancellable> timersList : this.timersBroadcast.values()) {
            for (Cancellable timer : timersList) {
                timer.cancel();
            }
        }
        // if coordinator crashes cancel all heartbeats
        if (this.isCoordinator) {
            for (Cancellable timer : this.coordinatorHeartbeatTimeouts) {
                timer.cancel();
            }
            // access all timeouts in timersBroadcastCohorts
            for (HashMap<MessageTypes, List<Cancellable>> timersCohort : this.timersBroadcastCohorts.values()) {
                for (List<Cancellable> timersList : timersCohort.values()) {
                    for (Cancellable timer : timersList) {
                        timer.cancel();
                    }
                }
            }
        }
    }

    private void cancelCohortTimeouts(ActorRef crashedCohort) {
        // I have the crashed cohort and I have to remove the timers for him
        assert this.isCoordinator;
        HashMap<MessageTypes, List<Cancellable>> timersCohort = this.timersBroadcastCohorts.get(crashedCohort);
        for (List<Cancellable> timersList : timersCohort.values()) {
            for (Cancellable timer : timersList) {
                timer.cancel();
            }
        }
    }

    private void onHeartbeatTimeout(MessageTypes topic) throws InterruptedException {
        System.out.println(getSelf().path().name() + " detected " + this.coordinator.path().name() + " crashed, no " + topic);
        this.logger.logCrash(getSelf().path().name(), this.coordinator.path().name(), topic);
        this.startLeaderElection(topic);
    }

    private void onUpdateRequestTimeout(MessageTypes cause) throws InterruptedException {
        System.out.println(getSelf().path().name() + " detected coordinator crashed due to no " + cause);
        this.logger.logCrash(getSelf().path().name(), this.coordinator.path().name(), cause);

        // send the start election message to all cohorts
        for (ActorRef cohort : this.cohorts) {
            CommunicationWrapper.send(cohort, new Message<>(MessageTypes.START_ELECTION, this.cohorts), getSelf());
        }
    }

    private void onUpdateTimeout(MessageTypes cause, ActorRef crashedCohort) {
        this.logger.logCrash(getSelf().path().name(), crashedCohort.path().name(), cause);
        // remove the timeouts for the crashed cohort
        this.cancelCohortTimeouts(crashedCohort);
    }

    private void onACKTimeout(MessageTypes cause) throws InterruptedException {
        System.out.println("Cohort " + getSelf().path().name() + " detected " + this.coordinator.path().name() + " crashed due to no " + cause);
        this.logger.logCrash(getSelf().path().name(), this.coordinator.path().name(), cause);
        this.startLeaderElection(cause);
    }

    // detects crash of the successor during election
    private void onElectionSuccessorTimeout(MessageTypes cause, ActorRef sender) {
        System.out.println("Cohort " + getSelf().path().name() + " detected " + sender.path().name() + " crashed due to no " + cause + " ELECTION MODE");
        // TODO if the successor crashes, we have to remove him from the list of cohorts
    }


    /************************************************************************
     *
     *  MESSAGE CALLBACKS
     *
     ************************************************************************/


    /***WE ARE IN STANDARD MODE, AND WE RECEIVED A MESSAGE_ELECTION***/
    private void onElectionMsgInStdMode(MessageElection<?> message) throws InterruptedException {
        // if the message is not ELECTION is a late message from the election, so we ignore it
        if (message.topic != MessageTypes.ELECTION) {
            return;
        }

        // some old election message may arrive when we finished election, if so ignore them
        assert message.payload instanceof HashMap<?, ?>;
        @SuppressWarnings("unchecked") // Suppresses unchecked warning for this specific cast
        HashMap<ActorRef, UpdateIdentifier> cohortUpdateIdMap = (HashMap<ActorRef, UpdateIdentifier>) message.payload;
        UpdateIdentifier senderUpdateId = cohortUpdateIdMap.get(getSender());
        // if the sender has an older epoch than mine, ignore the message
        if (senderUpdateId.getEpoch() < this.updateIdentifier.getEpoch()) {
            return;
        }

        System.out.println(getSelf().path().name() + " detected " + this.coordinator.path().name() + " crashed due to " + message.topic);

        this.cohorts.remove(this.coordinator);
        // here we don't know the crash cause
        this.logger.logCrash(getSelf().path().name(), this.coordinator.path().name(), message.topic);
        onStartElection(message.topic, this.cohorts);
        // send to myself the election message
        CommunicationWrapper.send(getSelf(), message, getSender());
    }

    /***NORMAL MODE MESSAGES HANDLER***/
    private void onMessage(Message<?> message) throws InterruptedException {
        ActorRef sender = getSender();
        Pair<UpdateIdentifier, Integer> payload;
        switch (message.topic) {
            case SET_COORDINATOR:
                assert message.payload instanceof ActorRef;
                onSetCoordinator((ActorRef) message.payload);
                break;
            case SET_NEIGHBORS:
                assert message.payload instanceof List<?>;
                if (InstanceController.isNeighborListCorrect((List<?>) message.payload)) {
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
                assert message.payload instanceof Pair<?, ?>;
                payload = InstanceController.unpackUpdateIDState((Pair<?, ?>) message.payload);
                onUpdate(payload.getFirst(), payload.getSecond(), message.topic);
                break;
            case ACK:
                assert message.payload instanceof UpdateIdentifier;
                onACK(sender, (UpdateIdentifier) message.payload, message.topic);
                break;
            case WRITEOK:
                assert message.payload instanceof Pair<?, ?>;
                payload = InstanceController.unpackUpdateIDState((Pair<?, ?>) message.payload);
                onWriteOk(payload.getFirst(), payload.getSecond());
                break;
            case REMOVE_CRASHED:
                assert message.payload instanceof ActorRef;
                onRemoveCrashed((ActorRef) message.payload);
                break;
            case HEARTBEAT:
                onHeartbeat(sender);
                break;
            case START_ELECTION:
                assert message.payload instanceof List<?>;
                if (InstanceController.isNeighborListCorrect((List<?>) message.payload)) {
                    // This cast is safe because we've checked all elements are ActorRef instances
                    @SuppressWarnings("unchecked") // Suppresses unchecked warning for this specific cast
                    List<ActorRef> actorList = (List<ActorRef>) message.payload;
                    onStartElection(message.topic, actorList);
                } else {
                    throw new InterruptedException("Error: Payload contains non-ActorRef elements.");
                }
                break;
            default:
                System.out.println(getSelf().path().name() + " Received message: " + message.topic + " with payload: " + message.payload + " from " + sender.path().name());
        }
    }

    /***TIMEOUT MESSAGES HANDLER***/
    private void onTimeout(MessageTimeout<?> message) throws InterruptedException {
        ActorRef crashedCohort = getSender();
        this.cohorts.remove(crashedCohort);
        // update my neighbors
        onSetNeighbors(this.cohorts);
        switch (message.topic) {
            case HEARTBEAT:
                assert message.payload == null;
                onHeartbeatTimeout(message.topic);
                break;
            case UPDATE_REQUEST:
                assert message.payload == MessageTypes.UPDATE;
                onUpdateRequestTimeout((MessageTypes) message.payload);
                break;
            case UPDATE:
                assert message.payload == MessageTypes.ACK;
                onUpdateTimeout((MessageTypes) message.payload, crashedCohort);
                break;
            case ACK:
                assert message.payload == MessageTypes.WRITEOK;
                onACKTimeout((MessageTypes) message.payload);
                break;
            case ELECTION:
                assert message.payload == MessageTypes.ACK;
                onElectionSuccessorTimeout((MessageTypes) message.payload, crashedCohort);
                break;
            default:
                System.out.println("Received unknown timeout: " + message.topic);
        }
    }

    /***LEADER ELECTION MESSAGES HANDLER***/
    private void onElectionMessageHandler(MessageElection<?> message) throws InterruptedException {
        ActorRef sender = getSender();
        switch (message.topic) {
            case ELECTION:
                if (InstanceController.isCohortMapLeaderElectionCorrect((HashMap<?, ?>) message.payload)) {
                    @SuppressWarnings("unchecked") // Suppresses unchecked warning for this specific cast
                    HashMap<ActorRef, UpdateIdentifier> map = (HashMap<ActorRef, UpdateIdentifier>) message.payload;
                    onElection(sender, map);
                } else {
                    throw new InterruptedException("Error: Payload contains non-ActorRef elements.");
                }
                break;
            case ACK:
                assert message.payload == null;
                onACKElectionMode(sender);
                break;
            case SYNC:
                assert message.payload instanceof HashMap<?, ?>;
                if (InstanceController.isUpdateIDIntCorrect((HashMap<?, ?>) message.payload)) {
                    @SuppressWarnings("unchecked") // Suppresses unchecked warning for this specific cast
                    HashMap<UpdateIdentifier, Integer> map = (HashMap<UpdateIdentifier, Integer>) message.payload;
                    onSync(sender, map);
                } else {
                    throw new InterruptedException("Error: Payload contains non-ActorRef elements.");
                }
                break;
            default:
                System.out.println("UNKNOWN" + getSelf().path().name() + " Received message: " + message.topic + " with payload: " + message.payload);
                break;
        }
    }

    /***WE ARE IN ELECTION MODE AND RECEIVED AN NORMAL MESSAGE***/
    private void onStdMsgInElectionMode(Message<?> message) {
        switch (message.topic) {
            case READ_REQUEST:
                // TODO if we receive a read just return the value
                break;
            case UPDATE_REQUEST:
                // TODO if we receive an update request, we save it for later
                break;
            default:
                System.out.println(getSelf().path().name() + " std msg received " + message.topic + " " + message.payload + " from " + getSender().path().name());
                break;
        }
    }


    /************************************************************************
     *
     *  COMMAND MESSAGE CALLBACK
     *
     ************************************************************************/


    // This method is called when the cohort receives a CRASH message
    private void onCommandCrash() {
        getContext().become(crashed());
        this.isCrashed = true;
        // the cohorts cancel the timeout for the heartbeat
        this.cancelAllTimeouts();
    }

    /***COMMAND MESSAGES HANDLER***/
    private void onCommandMsg(MessageCommand message) {
        switch (message.topic) {
            case CRASH -> onCommandCrash();
            case CRASH_NO_WRITEOK -> this.noWriteOkResponse = true;
            case CRASH_ONLY_ONE_WRITEOK -> this.onlyOneWriteOkRes = true;
            default -> System.out.println(getSelf().path().name() + " Received unknown command: " + message.topic);
        }
    }

    // Here we define the mapping between the received message types and our actor methods
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                //careful! here MessageTimeout is a Message, so we first have to eval this one!
                .match(MessageTimeout.class, this::onTimeout)
                .match(MessageCommand.class, this::onCommandMsg)
                .match(MessageElection.class, this::onElectionMsgInStdMode)
                .match(Message.class, this::onMessage)
                .build();
    }

    final Receive leader_election() {
        return receiveBuilder()
                .match(MessageTimeout.class, this::onTimeout)
                .match(MessageElection.class, this::onElectionMessageHandler)
                .match(Message.class, this::onStdMsgInElectionMode)
                .build();
    }

    final Receive crashed() {
        return receiveBuilder()
                .matchAny(msg -> {
                })
                .build();
    }
}
