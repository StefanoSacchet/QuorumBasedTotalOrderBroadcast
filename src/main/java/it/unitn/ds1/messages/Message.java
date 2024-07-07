package it.unitn.ds1.messages;

import java.io.Serializable;

// This class represents a message our actor will receive
public class Message<T> implements Serializable {
    public final MessageTypes topic;
    public final T payload;

    public Message(MessageTypes topic, T payload) {
        this.topic = topic;
        this.payload = payload;
    }

}
