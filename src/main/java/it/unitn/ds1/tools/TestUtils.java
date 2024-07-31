package it.unitn.ds1.tools;

import akka.actor.ActorRef;

public class TestUtils {
    public static int getUpdateValueFromClient(ActorRef clientRequest) {
        return Integer.parseInt(clientRequest.path().name().split("_")[1]) * 1000000;
    }

    public static String getCohortFromClient(ActorRef client) {
        return client.path().name().replace("client", "cohort");
    }
}
