package it.unitn.ds1.tools;

import io.github.cdimascio.dotenv.Dotenv;

public class DotenvLoader {
    private static DotenvLoader instance;
    private final Dotenv dotenv;

    public DotenvLoader() {
        this.dotenv = Dotenv.configure().directory("./config").load();

        int MAX_RTT = Integer.parseInt(dotenv.get("MAX_RTT"));
        int HEARTBEAT = Integer.parseInt(dotenv.get("HEARTBEAT_INTERVAL"));
        int HEARTBEAT_TIMEOUT = Integer.parseInt(dotenv.get("HEARTBEAT_TIMEOUT"));
        int TIMEOUT = Integer.parseInt(dotenv.get("TIMEOUT"));

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
        return Integer.parseInt(dotenv.get("N_COHORTS"));
    }

    public int getRTT() {
        return Integer.parseInt(dotenv.get("MAX_RTT"));
    }

    public int getHeartbeat() {
        return Integer.parseInt(dotenv.get("HEARTBEAT_INTERVAL"));
    }

    public int getHeartbeatTimeout() {
        return Integer.parseInt(dotenv.get("HEARTBEAT_TIMEOUT"));
    }
}
