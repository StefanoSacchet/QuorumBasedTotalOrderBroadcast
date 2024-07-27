package it.unitn.ds1.tools;

import io.github.cdimascio.dotenv.Dotenv;

public class DotenvLoader {
    private int HEARTBEAT_TIMEOUT;
    private static DotenvLoader instance;
    private final Dotenv dotenv;
    private int N_COHORTS;

    public DotenvLoader() {
        this.dotenv = Dotenv.configure().directory("./config").load();
        this.N_COHORTS = Integer.parseInt(dotenv.get("N_COHORTS"));

        int MAX_RTT = Integer.parseInt(dotenv.get("MAX_RTT"));
        int HEARTBEAT = Integer.parseInt(dotenv.get("HEARTBEAT_INTERVAL"));
        this.HEARTBEAT_TIMEOUT = Integer.parseInt(dotenv.get("HEARTBEAT_TIMEOUT"));
        int TIMEOUT = Integer.parseInt(dotenv.get("TIMEOUT"));

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
        return Integer.parseInt(dotenv.get("TIMEOUT"));
    }

    public int getNCohorts() {
        return this.N_COHORTS;
    }

    public int getRTT() {
        return Integer.parseInt(dotenv.get("MAX_RTT"));
    }

    public int getHeartbeat() {
        return Integer.parseInt(dotenv.get("HEARTBEAT_INTERVAL"));
    }

    public int getHeartbeatTimeout() {
        return this.HEARTBEAT_TIMEOUT;
    }

    public void setHeartbeatTimeout(int timeout) {
        this.HEARTBEAT_TIMEOUT = timeout;
    }

    public void setNCohorts(int newNCohorts) {
        this.N_COHORTS = newNCohorts;
    }

    public int getElectionTimeout() {
        return Integer.parseInt(dotenv.get("ELECTION_TIMEOUT"));
    }
}
