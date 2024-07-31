package it.unitn.ds1.tools;

import akka.actor.ActorRef;
import it.unitn.ds1.classes.Pair;
import it.unitn.ds1.classes.UpdateIdentifier;
import it.unitn.ds1.messages.MessageTypes;

import java.util.HashMap;
import java.util.List;

public final class InstanceController {
    private InstanceController() {
        throw new IllegalStateException("Cannot instance a static class");
    }

    public static boolean isNeighborListCorrect(List<?> neighbors) {
        // Check if all elements in the list are instances of ActorRef
        return neighbors.stream().allMatch(element -> element instanceof ActorRef);
    }

    public static boolean isPayloadElectionTimeoutCorrect(Pair<?, ?> payload) {
        return payload.getFirst() instanceof MessageTypes && payload.getSecond() instanceof HashMap<?, ?>;
    }

    public static boolean isCohortMapLeaderElectionCorrect(HashMap<?, ?> map) {
        return map.keySet().stream().allMatch(element -> element instanceof ActorRef) &&
                map.values().stream().allMatch(element -> element instanceof UpdateIdentifier);
    }

    public static boolean isUpdateIDIntCorrect(HashMap<?, ?> map) {
        return map.keySet().stream().allMatch(element -> element instanceof UpdateIdentifier) &&
                map.values().stream().allMatch(element -> element instanceof Integer);
    }

    public static boolean isUpdatePayloadCorrect(Pair<?, ?> payload) {
        return payload.getFirst() instanceof UpdateIdentifier && payload.getSecond() instanceof Integer;
    }

    public static Pair<UpdateIdentifier, Integer> unpackUpdateIDState(Pair<?, ?> msgPayload) throws InterruptedException {
        if (isUpdatePayloadCorrect(msgPayload)) {
            @SuppressWarnings("unchecked") // Suppresses unchecked warning for this specific cast
            Pair<UpdateIdentifier, Integer> payload = (Pair<UpdateIdentifier, Integer>) msgPayload;
            UpdateIdentifier updateID = payload.getFirst();
            int newState = payload.getSecond();
            return new Pair<>(updateID, newState);
        } else {
            throw new InterruptedException("Error: Update payload contains non-UpdateIdentifier and non-Integer elements.");
        }
    }
}
