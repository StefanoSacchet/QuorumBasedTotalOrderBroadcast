package it.unitn.ds1.tools;

import akka.actor.ActorRef;
import it.unitn.ds1.messages.Message;
import it.unitn.ds1.messages.MessageCrash;
import it.unitn.ds1.messages.MessageCommand;

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

    public static void send(ActorRef receiver, MessageCommand message) throws InterruptedException {
        receiver.tell(message, ActorRef.noSender());
    }
    public static void send(ActorRef receiver, MessageCommand message, ActorRef sender) throws InterruptedException {
        receiver.tell(message, sender);
    }
}
