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
    START_ELECTION,
    ELECTION,
    SYNC,
    ACK,
    CRASH,
    CRASH_NO_WRITEOK, // used only to make coordinator crash without sending writeok
    CRASH_ONLY_ONE_WRITEOK, // used only to make coordinator crash after sending only one writeok
    TIMEOUT,
    TEST_READ,
    TEST_UPDATE,
    REMOVE_CRASHED,
}
