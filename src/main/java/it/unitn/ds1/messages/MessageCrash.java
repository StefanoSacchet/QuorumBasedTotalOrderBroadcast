package it.unitn.ds1.messages;

import java.io.Serializable;

public class MessageCrash implements Serializable {
    public final MessageTypes topic;

    public MessageCrash() {
        this.topic = MessageTypes.CRASH;
    }
}
