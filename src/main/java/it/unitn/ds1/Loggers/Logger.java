package it.unitn.ds1.Loggers;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.nio.file.Path;

public abstract class Logger {

    Path dir;
    Path path;

    public Logger(String pathString) {
        this.path = Paths.get(pathString);
        this.dir = Paths.get(pathString.split("/")[1]);
        try {
            synchronized (Logger.class){
                if (!Files.exists(this.dir)) {
                    Files.createDirectories(this.dir);
                }
                if (!Files.exists(this.path)) {
                    Files.createFile(this.path);
                }
            }
        } catch (IOException e) {
            System.err.println("Error creating log file: " + e.getMessage());
        }
    }

    public static void clearFile(String pathString) {
        Path tmp = Paths.get(pathString);
        if (Files.exists(tmp)) {
            try {
                Files.delete(tmp);
            } catch (IOException e) {
                System.err.println("Error deleting log file: " + e.getMessage());
            }
        }
    }




}
