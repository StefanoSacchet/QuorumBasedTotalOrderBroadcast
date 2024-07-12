package it.unitn.ds1.messages;

import it.unitn.ds1.tools.DotenvLoader;

import java.io.Serializable;

// This class represents a message our actor will receive
public class Message<T> implements Serializable {
    public final MessageTypes topic;
    public final T payload;
    public final double roundTripTime;

    public Message(MessageTypes topic, T payload) {
        this.topic = topic;
        this.payload = payload;
        this.roundTripTime = Math.random()* DotenvLoader.getInstance().getRTT();
    }

}

