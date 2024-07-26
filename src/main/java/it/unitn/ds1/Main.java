package it.unitn.ds1;

import java.util.List;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import it.unitn.ds1.messages.MessageCommand;
import it.unitn.ds1.messages.MessageTypes;
import it.unitn.ds1.tools.CommunicationWrapper;
import it.unitn.ds1.tools.InUtils;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        InUtils testUtils = new InUtils();
        List<ActorRef> cohorts = testUtils.cohorts;
        List<ActorRef> clients = testUtils.clients;
        ActorSystem system = testUtils.system;

        CommunicationWrapper.send(cohorts.get(0), new MessageCommand(MessageTypes.CRASH));

        InUtils.threadSleep(3500);

        CommunicationWrapper.send(clients.get(2), new MessageCommand(MessageTypes.TEST_UPDATE));

        InUtils.threadSleep(3000);

        CommunicationWrapper.send(clients.get(3), new MessageCommand(MessageTypes.TEST_READ));

        InUtils.threadSleep(3000);
        system.terminate();
    }
}
