package it.unitn.ds1.loggers;

public enum LogType {
    UPDATE,
    READ_REQ,
    READ_DONE,

    // crashes
    CLIENT_DETECTS_COHORT_CRASH,
    COHORT_DETECTS_COHORT_CRASH,
}
