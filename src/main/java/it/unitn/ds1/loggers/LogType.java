package it.unitn.ds1.loggers;

public enum LogType {
    UPDATE,
    READ_REQ,
    READ_DONE,
    UPDATE_REQ,

    // crashes
    CLIENT_DETECTS_COHORT_CRASH,
    COHORT_DETECTS_COHORT_CRASH,

    // leader election
    LEADER_ELECTION_START,
}
