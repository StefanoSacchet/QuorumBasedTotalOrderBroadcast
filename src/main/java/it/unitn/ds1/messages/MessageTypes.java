package it.unitn.ds1.messages;

public enum MessageTypes {
    READ,
    READ_REQUEST,
    UPDATE_REQUEST,
    UPDATE,
    WRITEOK,
    SET_COORDINATOR,
    SET_NEIGHBORS,
    HEARTBEAT,
    ELECTION,
    SYNC,
    ACK,
    CRASH,
    REMOVE_CRASHED,
    // todo aggiungere i messaggi per 2pc

}


