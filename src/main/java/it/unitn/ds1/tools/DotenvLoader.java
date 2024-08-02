package it.unitn.ds1.tools;

import io.github.cdimascio.dotenv.Dotenv;

public class DotenvLoader {
    private static DotenvLoader instance;
    private final Dotenv dotenv;

    private int TIMEOUT;
    private int N_COHORTS;
    private int MAX_RTT;
    private int HEARTBEAT;
    private int HEARTBEAT_TIMEOUT;
    private int ELECTION_TIMEOUT;

    public DotenvLoader() {
        this.dotenv = Dotenv.configure().directory("./config").load();

        this.TIMEOUT = Integer.parseInt(dotenv.get("TIMEOUT"));
        this.N_COHORTS = Integer.parseInt(dotenv.get("N_COHORTS"));
        this.MAX_RTT = Integer.parseInt(dotenv.get("MAX_RTT"));
        this.HEARTBEAT = Integer.parseInt(dotenv.get("HEARTBEAT_INTERVAL"));
        this.HEARTBEAT_TIMEOUT = Integer.parseInt(dotenv.get("HEARTBEAT_TIMEOUT"));
        this.ELECTION_TIMEOUT = Integer.parseInt(dotenv.get("ELECTION_TIMEOUT"));

        assert N_COHORTS > 2;
        assert MAX_RTT < TIMEOUT;
        assert MAX_RTT < HEARTBEAT_TIMEOUT;
        assert HEARTBEAT + MAX_RTT < HEARTBEAT_TIMEOUT;
    }

    public static synchronized DotenvLoader getInstance() {
        if (instance == null) {
            instance = new DotenvLoader();
        }
        return instance;
    }

    public String getLogPath() {
        return dotenv.get("LOG_PATH");
    }

    public int getTimeout() {
        return this.TIMEOUT;
    }

    public void setTimeout(int timeout) {
        this.TIMEOUT = timeout;
    }

    public int getNCohorts() {
        return this.N_COHORTS;
    }

    public void setNCohorts(int newNCohorts) {
        this.N_COHORTS = newNCohorts;
    }

    public int getRTT() {
        return this.MAX_RTT;
    }

    public void setRTT(int newRTT) {
        this.MAX_RTT = newRTT;
    }

    public int getHeartbeat() {
        return this.HEARTBEAT;
    }

    public void setHeartbeat(int newHeartbeat) {
        this.HEARTBEAT = newHeartbeat;
    }

    public int getHeartbeatTimeout() {
        return this.HEARTBEAT_TIMEOUT;
    }

    public void setHeartbeatTimeout(int timeout) {
        this.HEARTBEAT_TIMEOUT = timeout;
    }

    public int getElectionTimeout() {
        return this.ELECTION_TIMEOUT;
    }

    public void setElectionTimeout(int newElectionTimeout) {
        this.ELECTION_TIMEOUT = newElectionTimeout;
    }
}
