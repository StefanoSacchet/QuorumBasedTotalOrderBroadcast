package it.unitn.ds1.messages;

import java.io.Serializable;

public class MessageCommand implements Serializable {
    public final MessageTypes topic;

    public MessageCommand(MessageTypes topic) {
        this.topic = topic;
    }
}
