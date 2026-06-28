package com.craftion.farmer.economy;

import com.craftion.farmer.debug.DebugLogger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class VaultEconomyProvider implements EconomyProvider {

    private static final String ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy";

    private final JavaPlugin plugin;
    private final DebugLogger debugLogger;
    private volatile Object economy;
    private volatile Method depositPlayerMethod;
    private volatile Method nameMethod;

    public VaultEconomyProvider(JavaPlugin plugin, DebugLogger debugLogger) {
        this.plugin = plugin;
        this.debugLogger = debugLogger;
        refresh();
    }

    @Override
    public synchronized void refresh() {
        this.economy = null;
        this.depositPlayerMethod = null;
        this.nameMethod = null;

        try {
            Class<?> economyClass = Class.forName(ECONOMY_CLASS);
            RegisteredServiceProvider<?> registration = registration(economyClass);
            if (registration == null || registration.getProvider() == null) {
                this.debugLogger.debug("Vault economy service is not registered.");
                return;
            }

            this.economy = registration.getProvider();
            this.depositPlayerMethod = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            this.nameMethod = economyClass.getMethod("getName");
            this.debugLogger.debug("Vault economy provider ready: " + name());
        } catch (ClassNotFoundException exception) {
            this.debugLogger.debug("Vault economy API is not available.");
        } catch (ReflectiveOperationException exception) {
            this.plugin.getLogger().warning("Vault economy hook hazirlanamadi: " + readableMessage(exception));
        }
    }

    @Override
    public String name() {
        Object provider = this.economy;
        Method method = this.nameMethod;
        if (provider == null || method == null) {
            return "Vault";
        }

        try {
            Object value = method.invoke(provider);
            return value == null || value.toString().isBlank() ? "Vault" : value.toString();
        } catch (ReflectiveOperationException exception) {
            return "Vault";
        }
    }

    @Override
    public boolean isAvailable() {
        if (this.economy == null) {
            refresh();
        }
        return this.economy != null && this.depositPlayerMethod != null;
    }

    @Override
    public EconomyDepositResult deposit(OfflinePlayer player, double amount) {
        if (player == null || !Double.isFinite(amount) || amount <= 0.0D) {
            return EconomyDepositResult.failure(name(), "Invalid deposit amount.");
        }
        if (!isAvailable()) {
            return EconomyDepositResult.failure(name(), "Vault economy provider is unavailable.");
        }

        try {
            Object response = this.depositPlayerMethod.invoke(this.economy, player, amount);
            if (transactionSuccess(response)) {
                return EconomyDepositResult.success(name());
            }
            return EconomyDepositResult.failure(name(), errorMessage(response));
        } catch (ReflectiveOperationException exception) {
            return EconomyDepositResult.failure(name(), readableMessage(exception));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private RegisteredServiceProvider<?> registration(Class<?> economyClass) {
        return this.plugin.getServer().getServicesManager().getRegistration((Class) economyClass);
    }

    private boolean transactionSuccess(Object response) throws ReflectiveOperationException {
        if (response == null) {
            return false;
        }
        Method method = response.getClass().getMethod("transactionSuccess");
        Object value = method.invoke(response);
        return Boolean.TRUE.equals(value);
    }

    private String errorMessage(Object response) {
        if (response == null) {
            return "No economy response.";
        }

        try {
            Field field = response.getClass().getField("errorMessage");
            Object value = field.get(response);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return "Economy transaction failed.";
    }

    private String readableMessage(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message;
    }
}
