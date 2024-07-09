package it.unitn.ds1.tools;

import io.github.cdimascio.dotenv.Dotenv;

public class DotenvLoader {
    private static DotenvLoader instance;
    private final Dotenv dotenv;

    public DotenvLoader() {
        this.dotenv = Dotenv.configure().directory("./config").load();
    }

    public static synchronized DotenvLoader getInstance() {
        if (instance == null) {
            instance = new DotenvLoader();
        }
        return instance;
    }

    public String getLogPath(){
        return dotenv.get("LOG_PATH");
    }

    public int getTimeout(){
        return Integer.parseInt(dotenv.get("TIMEOUT"));
    }

    public int getNCohorts(){
        return Integer.parseInt(dotenv.get("N_COHORTS"));
    }
}
