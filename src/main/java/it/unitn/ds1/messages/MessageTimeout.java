package it.unitn.ds1.messages;

public class MessageTimeout<T> extends Message<T> {
    public MessageTimeout(MessageTypes topic, T payload) {
        super(topic, payload);
    }
}
