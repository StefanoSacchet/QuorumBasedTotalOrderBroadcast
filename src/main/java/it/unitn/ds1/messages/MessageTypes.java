package it.unitn.ds1.messages;

public enum MessageTypes {
    READ,
    READOK,
    UPDATE,
    WRITEOK,
    SET_COORDINATOR,
    SET_NEIGHBORS,
    HEARTBEAT,
    ELECTION,
    SYNC,
    // todo aggiungere i messaggi per 2pc
}


