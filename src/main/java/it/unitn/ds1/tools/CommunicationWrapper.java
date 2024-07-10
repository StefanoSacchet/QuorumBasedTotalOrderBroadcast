package it.unitn.ds1.tools;

import akka.actor.ActorRef;
import it.unitn.ds1.messages.Message;
import it.unitn.ds1.messages.MessageCrash;

public class CommunicationWrapper {

    public static void send(ActorRef receiver, Message<?> message, ActorRef sender) throws InterruptedException {
        try {
            long rtt = (long) (Math.random() * DotenvLoader.getInstance().getRTT());
            Thread.sleep(rtt);
            receiver.tell(message, sender);
        } catch (InterruptedException e) {
            throw new InterruptedException(e.getMessage());
        }
    }

    public static void send(ActorRef receiver, MessageCrash message) throws InterruptedException {
        receiver.tell(message, ActorRef.noSender());
    }
}
