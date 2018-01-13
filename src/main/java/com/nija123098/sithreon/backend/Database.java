package com.nija123098.sithreon.backend;

import com.nija123098.sithreon.backend.util.Log;
import com.nija123098.sithreon.backend.util.ThreadMaker;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * A simple key-value database.
 *
 * @author nija123098
 */
public enum Database {
    /**
     * Stores the millis of the last update handled by the SITHREON network
     */
    REPO_LAST_UPDATE("0"),
    /**
     * Pairings of competitors for the record of wins and losses
     */
    WINNER_PAIRING("NULL"),;

    /**
     * Stores if the database has been altered to prevent duplicate saves.
     */
    private static final AtomicBoolean DATABASE_CHANGE = new AtomicBoolean();
    private final Map<String, String> map = new HashMap<>();
    private final String def;

    /**
     * Constructs an instance to act as a category for a key-value system.
     *
     * @param def the default value to return when there is no entry.
     */
    Database(String def) {
        this.def = def;
    }

    /**
     * Initializes the database by loading and setting up automatic saving.
     */
    public static void init() {
        Path dataFile = Paths.get("data");// The root data path

        File[] files = dataFile.toFile().listFiles();
        Optional<Long> lastDataTime = Stream.of(files == null ? new File[0] : files).map(file -> Long.parseLong(file.getName())).reduce(Math::max);
        lastDataTime.ifPresent(aLong -> {// loads the most up to date database file
            for (Database database : Database.values()) {
                try {
                    Files.readAllLines(database.getPath(aLong)).forEach(s -> {
                        int index = s.indexOf('=');
                        database.map.put(s.substring(0, index), s.substring(index + 1, s.length()));
                    });
                } catch (IOException e) {
                    Log.ERROR.log("Could not read database", e);
                }
            }
        });

        Runnable save = () -> {// Specifies the runnable that saves the current version of the database
            if (!DATABASE_CHANGE.get()) return;
            DATABASE_CHANGE.set(false);
            long time = System.currentTimeMillis();
            Path dataTimeFile = dataFile.resolve(Long.toString(time));
            try {
                Files.createDirectories(dataTimeFile);
            } catch (IOException e) {
                Log.ERROR.log("IOException making data directory", e);
            }
            for (Database database : Database.values()) {
                List<String> data = new ArrayList<>();
                database.map.forEach((key, value) -> data.add(key + "=" + value));
                try {
                    Files.write(database.getPath(time), data, StandardOpenOption.CREATE);
                } catch (IOException e) {
                    try {
                        Files.walk(dataTimeFile, FileVisitOption.FOLLOW_LINKS).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                    } catch (IOException ex) {
                        Log.ERROR.log("Could not walk deleting uncompleted files", ex);
                    }
                    Log.ERROR.log("Could not complete writing database", e);
                }
            }
        };

        // Makes regular backups of the db
        new ScheduledThreadPoolExecutor(1, (r) -> ThreadMaker.getThread(ThreadMaker.BACKEND, "Database Scheduled Saver", true, r)).scheduleWithFixedDelay(save, 2, 2, TimeUnit.HOURS);
        Runtime.getRuntime().addShutdownHook(ThreadMaker.getThread(ThreadMaker.BACKEND, "Database Save Hook", false, save));// Saves the database on shutdown
    }

    public void put(Object key, String value) {
        DATABASE_CHANGE.set(true);
        this.map.put(key.toString(), value);
    }

    public String get(Object key) {
        return this.map.getOrDefault(key.toString(), this.def);
    }

    public void reset(Object key) {
        DATABASE_CHANGE.set(true);
        this.map.remove(key.toString());
    }

    public Set<String> keySet() {
        return this.map.keySet();
    }

    public void forEach(BiConsumer<String, String> consumer) {
        this.map.forEach(consumer);
    }

    private Path getPath(long time) {
        return Paths.get("data", Long.toString(time), this.name().toLowerCase() + ".txt");
    }
}
