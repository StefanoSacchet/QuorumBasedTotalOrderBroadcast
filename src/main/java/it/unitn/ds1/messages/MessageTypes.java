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
    CRASHNOWRITEOK, // used only to make coordinator crash without sending writeok
    TIMEOUT,
    TEST_READ,
    TEST_UPDATE,
    REMOVE_CRASHED,
}
