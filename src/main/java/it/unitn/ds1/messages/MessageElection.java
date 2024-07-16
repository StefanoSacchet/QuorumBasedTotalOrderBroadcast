package it.unitn.ds1.messages;

public class MessageElection<T> extends Message<T>{
    public MessageElection(MessageTypes topic, T payload) {
        super(topic, payload);
    }
}
