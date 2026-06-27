package com.craftion.farmer.storage;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.scheduler.SchedulerAdapter;
import com.craftion.farmer.storage.migration.MigrationRunner;
import com.craftion.farmer.storage.migration.V1InitialSchema;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.plugin.java.JavaPlugin;

public final class DatabaseManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final SchedulerAdapter schedulerAdapter;
    private final DebugLogger debugLogger;
    private final MigrationRunner migrationRunner;
    private final AtomicReference<ConnectionProvider> provider = new AtomicReference<>();
    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final AtomicLong generation = new AtomicLong(0L);
    private volatile DatabaseSettings settings;
    private volatile boolean available;

    public DatabaseManager(JavaPlugin plugin, ConfigManager configManager, SchedulerAdapter schedulerAdapter, DebugLogger debugLogger) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.schedulerAdapter = schedulerAdapter;
        this.debugLogger = debugLogger;
        this.migrationRunner = new MigrationRunner(List.of(new V1InitialSchema()));
    }

    public void initialize() {
        DatabaseSettings nextSettings = DatabaseSettings.from(this.configManager);
        scheduleInitialize(nextSettings, "Database baslatiliyor");
    }

    public void reload() {
        DatabaseSettings nextSettings = DatabaseSettings.from(this.configManager);
        if (nextSettings.equals(this.settings)) {
            this.debugLogger.debug("Database settings unchanged.");
            return;
        }

        scheduleInitialize(nextSettings, "Database ayarlari yenileniyor");
    }

    public void shutdown() {
        this.closing.set(true);
        this.generation.incrementAndGet();
        this.available = false;

        ConnectionProvider currentProvider = this.provider.getAndSet(null);
        if (currentProvider != null) {
            closeQuietly(currentProvider);
        }
    }

    public boolean isAvailable() {
        return this.available && this.provider.get() != null;
    }

    public <T> CompletableFuture<T> supplyAsync(SqlFunction<T> function) {
        CompletableFuture<T> future = new CompletableFuture<>();
        this.schedulerAdapter.runAsync(() -> {
            ConnectionProvider currentProvider = this.provider.get();
            if (currentProvider == null) {
                future.completeExceptionally(new IllegalStateException("Database connection is not available."));
                return;
            }

            try (Connection connection = currentProvider.getConnection()) {
                future.complete(function.apply(connection));
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    public CompletableFuture<Void> executeAsync(SqlConsumer consumer) {
        return supplyAsync(connection -> {
            consumer.accept(connection);
            return null;
        });
    }

    public CompletableFuture<Integer> executeUpdate(String sql, StatementBinder binder) {
        return supplyAsync(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                return statement.executeUpdate();
            }
        });
    }

    public <T> CompletableFuture<T> query(String sql, StatementBinder binder, ResultSetExtractor<T> extractor) {
        return supplyAsync(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return extractor.extract(resultSet);
                }
            }
        });
    }

    private void scheduleInitialize(DatabaseSettings nextSettings, String reason) {
        long initializeGeneration = this.generation.incrementAndGet();
        this.debugLogger.debug(reason + ": " + nextSettings.type());
        this.schedulerAdapter.runAsync(() -> initializeProvider(nextSettings, initializeGeneration));
    }

    private void initializeProvider(DatabaseSettings nextSettings, long initializeGeneration) {
        if (this.closing.get()) {
            return;
        }

        ConnectionProvider nextProvider = createProvider(nextSettings);
        try {
            nextProvider.initialize();
            this.migrationRunner.run(nextProvider, nextSettings.type());

            if (this.closing.get() || this.generation.get() != initializeGeneration) {
                closeQuietly(nextProvider);
                return;
            }

            ConnectionProvider previousProvider = this.provider.getAndSet(nextProvider);
            this.settings = nextSettings;
            this.available = true;

            closeQuietly(previousProvider);
            this.plugin.getLogger().info("Database baglantisi hazir: " + nextSettings.type());
        } catch (Exception exception) {
            closeQuietly(nextProvider);
            if (this.provider.get() == null) {
                this.available = false;
            }
            this.plugin.getLogger().severe("Database baslatilamadi: " + readableMessage(exception));
        }
    }

    private ConnectionProvider createProvider(DatabaseSettings settings) {
        return switch (settings.type()) {
            case SQLITE -> new SQLiteConnectionProvider(this.plugin.getDataFolder().toPath(), settings.sqliteFile());
            case MYSQL -> new MySQLConnectionProvider(settings);
        };
    }

    private void closeQuietly(ConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            return;
        }

        try {
            connectionProvider.close();
        } catch (Exception exception) {
            this.plugin.getLogger().warning("Database baglantisi kapatilirken hata olustu: " + readableMessage(exception));
        }
    }

    private String readableMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    public interface ResultSetExtractor<T> {
        T extract(ResultSet resultSet) throws SQLException;
    }
}
