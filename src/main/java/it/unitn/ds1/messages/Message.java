package it.unitn.ds1.messages;

import java.io.Serializable;

// This class represents a message our actor will receive
public class Message implements Serializable {
    public final String topic;
    public final String payload;

    public Message(String topic, String payload) {
        this.topic = topic;
        this.payload = payload;
    }
}
