package it.unitn.ds1.messages;

public class MessageCommand {
    public final MessageTypes topic;

    public MessageCommand(MessageTypes topic){
        this.topic = topic;
    }
}
