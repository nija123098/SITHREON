package com.nija123098.sithreon.backend;

import com.nija123098.sithreon.backend.machines.SuperServer;
import com.nija123098.sithreon.backend.objects.*;
import com.nija123098.sithreon.backend.util.FileUtil;
import com.nija123098.sithreon.backend.util.Log;
import com.nija123098.sithreon.backend.util.ThreadMaker;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A simple key-value database.
 *
 * @author nija123098
 */
public class Database<K, V> extends HashMap<K, V> {// todo use an actual DB
    /**
     * An array of the databases instances.
     */
    private static final List<Database<?, ?>> DATABASES = new ArrayList<>();

    /**
     * A list of registered repos where repos registered will always have true as their value.
     */
    public static final Database<Repository, Boolean> REGISTERED_REPOS = new Database<>(false, "registered_repos", Repository.class, Boolean.class);

    /**
     * Stores the commit hash of the last repo HEAD commit handled by the SITHREON network.
     */
    public static final Database<Repository, String> REPO_LAST_HEAD_HASH = new Database<>(null, "repo_last_update", Repository.class, String.class);

    /**
     * Storage of if the repository has been approved.
     */
    public static final Database<Repository, Boolean> REPO_APPROVAL = new Database<>(false, "repo_approval", Repository.class, Boolean.class);

    /**
     * Pairings of competitors for the record of wins and losses.
     */
    public static final Database<Repository, PriorityLevel> PRIORITY_LEVEL = new Database<>(PriorityLevel.MEDIUM, "priority_level", Repository.class, PriorityLevel.class);

    /**
     * A list of matches which the previous {@link SuperServer} uptime had not been able to complete.
     */
    public static final Database<Match, Boolean> MATCHES_TO_DO = new Database<>(false, "matches_to_do", Match.class, Boolean.class);

    /**
     * Stores a map of match ups and their victors where true represents the first repository's victory, and false represents the second repository's victory.
     */
    public static final Database<MatchUp, Lineup> MATCHUP_WINNERS = new Database<>(null, "first_victor", MatchUp.class, Lineup.class);

    /**
     * Stores if the database has been altered to prevent duplicate saves.
     */
    private static final AtomicBoolean DATABASE_CHANGE = new AtomicBoolean();

    private static final Map<Class<?>, Function<String, Object>> FROM_STRING_MAP = new HashMap<>();
    private static final Map<Class<?>, Function<Object, String>> TO_STRING_MAP = new HashMap<>();

    static {
        registerConversion(Repository.class, Repository::getRepo);
        registerConversion(Boolean.class, Boolean::parseBoolean);
        registerConversion(PriorityLevel.class, PriorityLevel::valueOf);
        registerConversion(String.class, Function.identity());
        registerConversion(MatchUp.class, MatchUp::new);
        registerConversion(Match.class, Match::new);
    }

    private static <E> void registerConversion(Class<E> clazz, Function<String, E> toObject, Function<E, String> toString) {
        FROM_STRING_MAP.put(clazz, (Function<String, Object>) toObject);
        TO_STRING_MAP.put(clazz, (Function<Object, String>) toString);
    }

    private static <E> void registerConversion(Class<E> clazz, Function<String, E> toObject) {
        registerConversion(clazz, toObject, Objects::toString);
    }

    /**
     * Initializes the database by loading and setting up automatic saving.
     */
    public static void init() {
        Path dataFile = Paths.get(Config.dataFolder);// The root data path

        File[] files = dataFile.toFile().listFiles();
        Optional<Long> lastDataTime = Stream.of(files == null ? new File[0] : files).map(file -> Long.parseLong(file.getName())).reduce(Math::max);
        lastDataTime.ifPresent(aLong -> {// loads the most up to date database file
            for (Database database : DATABASES) {
                if (!Files.exists(database.getPath(aLong))) continue;
                try {
                    database.loadData(Files.readAllLines(database.getPath(aLong), StandardCharsets.UTF_8));
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
            for (Database database : DATABASES) {
                try {
                    List<String> saveData = database.getSaveData();
                    if (!saveData.isEmpty()) {
                        Files.write(database.getPath(time), saveData, StandardCharsets.UTF_8, StandardOpenOption.CREATE);
                    }
                } catch (IOException e) {
                    try {
                        FileUtil.deleteFiles(dataTimeFile);
                    } catch (IOException ex) {
                        Log.ERROR.log("Could not either walk or delete incomplete files for " + dataTimeFile.toAbsolutePath(), ex);
                    }
                    Log.ERROR.log("Could not complete writing database", e);
                }
            }
        };

        // Makes regular backups of the db
        new ScheduledThreadPoolExecutor(1, r -> ThreadMaker.getThread(ThreadMaker.BACKEND, "Database Scheduled Saver", true, r)).scheduleWithFixedDelay(save, Config.databaseSaveDelay, Config.databaseSaveDelay, TimeUnit.MILLISECONDS);
        Runtime.getRuntime().addShutdownHook(ThreadMaker.getThread(ThreadMaker.BACKEND, "Database Save Hook", false, save));// Saves the database on shutdown
    }

    /**
     * The default value to return if no entry is found.
     */
    private final V def;

    /**
     * The name of this database table.
     */
    private final String name;

    /**
     * The class type for the key values.
     */
    private final Class<K> keyType;

    /**
     * The class type for value values.
     */
    private final Class<V> valueType;

    /**
     * Constructs an instance to act as a category for a simple key-value database system.
     *
     * @param def       the default value to return when there is no entry.
     * @param name      the name of the database table.
     * @param keyType   the key type.
     * @param valueType the value type.
     */
    private Database(V def, String name, Class<K> keyType, Class<V> valueType) {
        this.def = def;
        this.name = name;
        this.keyType = keyType;
        this.valueType = valueType;
        DATABASES.add(this);
    }

    @Override
    public V get(Object key) {
        return super.getOrDefault(key, this.def);
    }

    @Override
    public V put(K key, V value) {
        DATABASE_CHANGE.set(true);
        return value == this.def ? super.remove(key) : super.put(key, value);
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    /**
     * Gets the {@link Path} of the {@link Database} instance.
     *
     * @param time the time to save the value.
     * @return the {@link Path} of the {@link Database} save file.
     */
    private Path getPath(long time) {
        return Paths.get(Config.dataFolder, Long.toString(time), this.name + ".txt");
    }

    /**
     * Gets the data to represent the values of the {@link Database}.
     *
     * @return the data to represent the values of the {@link Database}.
     */
    private List<String> getSaveData() {
        return this.entrySet().stream().map(kvEntry -> TO_STRING_MAP.get(this.keyType).apply(kvEntry.getKey()) + "=" + TO_STRING_MAP.get(this.valueType).apply(kvEntry.getValue())).collect(Collectors.toList());
    }

    /**
     * Loads the data for this instance from the provided values.
     *
     * @param data the {@link String} representations of the {@link Database} values.
     */
    private void loadData(List<String> data) {
        this.clear();
        for (String s : data) {
            int index = s.indexOf('=');
            try {
                this.put((K) FROM_STRING_MAP.get(this.keyType).apply(s.substring(0, index)), (V) FROM_STRING_MAP.get(this.valueType).apply(s.substring(index + 1)));
            } catch (Exception e) {
                Log.WARN.log("Exception loading database value for line \"" + s + "\"", e);
            }
        }
    }
}
