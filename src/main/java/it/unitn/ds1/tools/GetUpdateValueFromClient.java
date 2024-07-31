package it.unitn.ds1.tools;

import akka.actor.ActorRef;

public class GetUpdateValueFromClient {
    public static int getValue(ActorRef clientRequest) {
        return Integer.parseInt(clientRequest.path().name().split("_")[1]) * 1000000;
    }
}
