package com.craftion.farmer.hook.visual;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.LocationSnapshot;
import com.craftion.farmer.scheduler.SchedulerAdapter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class FancyNpcsVisualProvider implements FarmerVisualProvider {

    private static final ApiPackage LEGACY_API = new ApiPackage(
        FancyNpcsApiMode.LEGACY,
        "de.oliver.fancynpcs.api.FancyNpcsPlugin",
        "de.oliver.fancynpcs.api.NpcData",
        "de.oliver.fancynpcs.api.skins.SkinData",
        "de.oliver.fancynpcs.api.Npc"
    );
    private static final List<ApiPackage> MODERN_APIS = List.of(
        new ApiPackage(
            FancyNpcsApiMode.MODERN,
            "com.fancyinnovations.fancynpcs.api.FancyNpcsPlugin",
            "com.fancyinnovations.fancynpcs.api.NpcData",
            "com.fancyinnovations.fancynpcs.api.skins.SkinData",
            "com.fancyinnovations.fancynpcs.api.Npc"
        ),
        new ApiPackage(
            FancyNpcsApiMode.MODERN,
            "io.github.fancyplugins.fancynpcs.api.FancyNpcsPlugin",
            "io.github.fancyplugins.fancynpcs.api.NpcData",
            "io.github.fancyplugins.fancynpcs.api.skins.SkinData",
            "io.github.fancyplugins.fancynpcs.api.Npc"
        ),
        new ApiPackage(
            FancyNpcsApiMode.MODERN,
            "com.fancyplugins.fancynpcs.api.FancyNpcsPlugin",
            "com.fancyplugins.fancynpcs.api.NpcData",
            "com.fancyplugins.fancynpcs.api.skins.SkinData",
            "com.fancyplugins.fancynpcs.api.Npc"
        )
    );

    private final JavaPlugin plugin;
    private final SchedulerAdapter schedulerAdapter;
    private final DebugLogger debugLogger;
    private final Consumer<Player> clickAction;
    private final FancyNpcsApiMode configuredApiMode;
    private final ApiBinding apiBinding;
    private final String idPrefix;
    private final boolean saveNpcsToFile;
    private final boolean removeOnFarmerDelete;
    private final EntityType npcType;
    private final String npcName;
    private final boolean glowing;
    private final NamedTextColor glowingColor;
    private final boolean turnToPlayer;
    private final float interactionCooldown;
    private final int visibilityDistance;

    public static Optional<FancyNpcsVisualProvider> create(
        JavaPlugin plugin,
        ConfigManager configManager,
        SchedulerAdapter schedulerAdapter,
        DebugLogger debugLogger,
        Consumer<Player> clickAction
    ) {
        FancyNpcsApiMode configuredApiMode = FancyNpcsApiMode.from(configManager.fancyNpcsApiMode());
        Optional<ApiBinding> binding = resolveBinding(configuredApiMode, debugLogger);
        return binding.map(value -> new FancyNpcsVisualProvider(plugin, configManager, schedulerAdapter, debugLogger, clickAction, configuredApiMode, value));
    }

    private FancyNpcsVisualProvider(
        JavaPlugin plugin,
        ConfigManager configManager,
        SchedulerAdapter schedulerAdapter,
        DebugLogger debugLogger,
        Consumer<Player> clickAction,
        FancyNpcsApiMode configuredApiMode,
        ApiBinding apiBinding
    ) {
        this.plugin = plugin;
        this.schedulerAdapter = schedulerAdapter;
        this.debugLogger = debugLogger;
        this.clickAction = clickAction == null ? player -> player.performCommand("farmer open") : clickAction;
        this.configuredApiMode = configuredApiMode;
        this.apiBinding = apiBinding;
        this.idPrefix = normalizeIdPrefix(configManager.fancyNpcsIdPrefix());
        this.saveNpcsToFile = configManager.saveFancyNpcsToFile();
        this.removeOnFarmerDelete = configManager.removeFancyNpcOnFarmerDelete();
        this.npcType = parseEntityType(configManager.npcType());
        this.npcName = normalizeNpcName(configManager.npcName());
        this.glowing = configManager.npcGlowing();
        this.glowingColor = parseTextColor(configManager.npcGlowingColor());
        this.turnToPlayer = configManager.npcTurnToPlayer();
        this.interactionCooldown = Math.max(0.0F, (float) configManager.npcInteractionCooldown());
        this.visibilityDistance = Math.max(0, configManager.npcVisibilityDistance());
    }

    @Override
    public VisualProviderType type() {
        return VisualProviderType.FANCY_NPCS;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void spawn(Farmer farmer) {
        if (farmer == null) {
            return;
        }

        this.schedulerAdapter.runGlobal(() -> runAtFarmerLocation(farmer, location -> rebindNow(farmer, location)));
    }

    @Override
    public void remove(Farmer farmer) {
        if (farmer != null && this.removeOnFarmerDelete) {
            this.schedulerAdapter.runGlobal(() -> runAtFarmerLocation(farmer, location -> removeNpcById(npcId(farmer.farmerId()), false, "farmer delete")));
        }
    }

    @Override
    public void remove(String farmerId) {
        if (farmerId == null || farmerId.isBlank() || !this.removeOnFarmerDelete) {
            return;
        }

        this.schedulerAdapter.runGlobal(() -> scheduleRemoveNpcById(npcId(farmerId), false, "farmer delete"));
    }

    @Override
    public void reconcile(Collection<Farmer> farmers) {
        if (farmers == null) {
            return;
        }

        List<Farmer> farmerList = List.copyOf(farmers);
        this.schedulerAdapter.runGlobal(() -> {
            removeStaleNpcs(farmerList);
            for (Farmer farmer : farmerList) {
                runAtFarmerLocation(farmer, location -> rebindNow(farmer, location));
            }
        });
    }

    @Override
    public void shutdown() {
        if (!this.saveNpcsToFile) {
            this.schedulerAdapter.runGlobal(this::removeAllManagedNpcs);
        }
    }

    private void runAtFarmerLocation(Farmer farmer, Consumer<Location> task) {
        Optional<Location> location = location(farmer.location());
        if (location.isEmpty()) {
            this.debugLogger.debug("FancyNPCs operation skipped because world is not loaded: " + farmer.farmerId());
            return;
        }

        this.schedulerAdapter.runAtLocation(location.get(), () -> task.accept(location.get()));
    }

    private void rebindNow(Farmer farmer, Location location) {
        String npcId = npcId(farmer.farmerId());
        try {
            Object manager = npcManager();
            Object existingNpc = invoke(manager, "getNpcById", new Class<?>[] {String.class}, npcId);
            if (existingNpc != null) {
                this.debugLogger.debug("Persistent FancyNPC found and rebinding: " + npcId);
                removeNpc(existingNpc, true);
            } else {
                this.debugLogger.debug("Persistent FancyNPC missing and creating: " + npcId);
            }

            Object npcData = createNpcData(npcId, farmer, location);
            Object pluginApi = fancyNpcsPlugin();
            @SuppressWarnings("unchecked")
            Function<Object, Object> adapter = (Function<Object, Object>) invoke(pluginApi, "getNpcAdapter");
            Object npc = adapter.apply(npcData);
            invokeIfPresent(npc, "setSaveToFile", new Class<?>[] {boolean.class}, this.saveNpcsToFile);
            invoke(manager, "registerNpc", new Class<?>[] {this.apiBinding.npcClass()}, npc);
            invoke(npc, "create");
            invoke(npc, "spawnForAll");
            saveNpcsIfNeeded(manager);
            this.debugLogger.debug("FancyNPCs farmer npc spawned: " + npcId + " mode=" + this.configuredApiMode + "/" + this.apiBinding.mode());
        } catch (RuntimeException | LinkageError exception) {
            this.debugLogger.debug("FancyNPCs farmer npc rebind failed: " + npcId + " reason=" + readableMessage(exception));
            this.plugin.getLogger().warning("FancyNPCs farmer NPC olusturulamadi: " + readableMessage(exception));
        }
    }

    private Object createNpcData(String npcId, Farmer farmer, Location location) {
        try {
            Consumer<Player> onClick = player -> this.schedulerAdapter.runAtEntity(player, () -> {
                this.debugLogger.debug("FancyNPCs farmer npc click callback fired: npc=" + npcId + " player=" + player.getName());
                this.clickAction.accept(player);
            });
            return this.apiBinding.npcDataConstructor().newInstance(
                npcId,
                npcId,
                farmer.ownerUuid(),
                this.npcName,
                null,
                location,
                false,
                true,
                false,
                this.glowing,
                this.glowingColor,
                this.npcType,
                new ConcurrentHashMap<>(),
                this.turnToPlayer,
                -1,
                onClick,
                new ConcurrentHashMap<>(),
                this.interactionCooldown,
                1.0F,
                this.visibilityDistance,
                new ConcurrentHashMap<>(),
                false
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("FancyNPCs NpcData could not be created.", exception);
        }
    }

    private void removeStaleNpcs(Collection<Farmer> farmers) {
        if (!this.removeOnFarmerDelete) {
            return;
        }

        Set<String> expectedIds = new HashSet<>();
        for (Farmer farmer : farmers) {
            expectedIds.add(npcId(farmer.farmerId()));
        }

        for (Object npc : allNpcs()) {
            String npcId = npcId(npc).orElse(null);
            if (npcId != null && npcId.startsWith(this.idPrefix) && !expectedIds.contains(npcId)) {
                scheduleRemoveNpc(npc, npcId, true, "stale");
            }
        }
    }

    private void removeAllManagedNpcs() {
        for (Object npc : allNpcs()) {
            String npcId = npcId(npc).orElse(null);
            if (npcId != null && npcId.startsWith(this.idPrefix)) {
                scheduleRemoveNpc(npc, npcId, true, "shutdown");
            }
        }
    }

    private void scheduleRemoveNpcById(String npcId, boolean force, String reason) {
        try {
            Object manager = npcManager();
            Object npc = invoke(manager, "getNpcById", new Class<?>[] {String.class}, npcId);
            if (npc == null) {
                return;
            }
            scheduleRemoveNpc(npc, npcId, force, reason);
        } catch (RuntimeException | LinkageError exception) {
            this.plugin.getLogger().warning("FancyNPCs farmer NPC silinemedi: " + readableMessage(exception));
        }
    }

    private void removeNpcById(String npcId, boolean force, String reason) {
        try {
            Object manager = npcManager();
            Object npc = invoke(manager, "getNpcById", new Class<?>[] {String.class}, npcId);
            if (npc == null) {
                return;
            }
            removeNpc(npc, force);
            if ("stale".equals(reason)) {
                this.debugLogger.debug("Stale FancyNPC removed: " + npcId);
            }
        } catch (RuntimeException | LinkageError exception) {
            this.plugin.getLogger().warning("FancyNPCs farmer NPC silinemedi: " + readableMessage(exception));
        }
    }

    private void scheduleRemoveNpc(Object npc, String npcId, boolean force, String reason) {
        Optional<Location> location = npcLocation(npc);
        if (location.isEmpty()) {
            this.debugLogger.debug("FancyNPCs farmer npc removal failed: " + npcId + " reason=location unavailable");
            return;
        }

        this.schedulerAdapter.runAtLocation(location.get(), () -> removeNpcById(npcId, force, reason));
    }

    private void removeNpc(Object npc, boolean force) {
        try {
            if (!force && !this.removeOnFarmerDelete) {
                return;
            }

            Object manager = npcManager();
            invoke(npc, "removeForAll");
            invoke(manager, "removeNpc", new Class<?>[] {this.apiBinding.npcClass()}, npc);
            saveNpcsIfNeeded(manager);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("FancyNPCs npc could not be removed.", exception);
        }
    }

    private Collection<?> allNpcs() {
        try {
            Object manager = npcManager();
            Object result = invoke(manager, "getAllNpcs");
            if (result instanceof Collection<?> collection) {
                return collection;
            }
        } catch (RuntimeException | LinkageError exception) {
            this.plugin.getLogger().warning("FancyNPCs farmer NPC listesi okunamadi: " + readableMessage(exception));
        }
        return Set.of();
    }

    private Object npcManager() {
        Object pluginApi = fancyNpcsPlugin();
        return invoke(pluginApi, "getNpcManager");
    }

    private Object fancyNpcsPlugin() {
        try {
            return this.apiBinding.pluginClass().getMethod("get").invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("FancyNPCs API is not available.", exception);
        }
    }

    private Optional<String> npcId(Object npc) {
        try {
            Object data = invoke(npc, "getData");
            Object id = invoke(data, "getId");
            if (id instanceof String value && !value.isBlank()) {
                return Optional.of(value);
            }
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<Location> npcLocation(Object npc) {
        try {
            Object data = invoke(npc, "getData");
            Object location = invoke(data, "getLocation");
            if (location instanceof Location value && value.getWorld() != null) {
                return Optional.of(value);
            }
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private String npcId(String farmerId) {
        return this.idPrefix + farmerId;
    }

    private Optional<Location> location(LocationSnapshot snapshot) {
        World world = Bukkit.getWorld(snapshot.world());
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch()));
    }

    private void saveNpcsIfNeeded(Object manager) {
        if (this.saveNpcsToFile) {
            invoke(manager, "saveNpcs", new Class<?>[] {boolean.class}, false);
        }
    }

    private Object invoke(Object target, String methodName) {
        return invoke(target, methodName, new Class<?>[0]);
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("FancyNPCs method failed: " + methodName, exception);
        }
    }

    private void invokeIfPresent(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            method.invoke(target, args);
        } catch (NoSuchMethodException exception) {
            this.debugLogger.debug("FancyNPCs method unavailable: " + methodName);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("FancyNPCs method failed: " + methodName, exception);
        }
    }

    private EntityType parseEntityType(String value) {
        if (value == null || value.isBlank()) {
            return EntityType.VILLAGER;
        }

        try {
            return EntityType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return EntityType.VILLAGER;
        }
    }

    private String normalizeIdPrefix(String value) {
        if (value == null || value.isBlank()) {
            return "craftion-farmer-";
        }
        return value.trim();
    }

    private String normalizeNpcName(String value) {
        if (value == null || value.isBlank()) {
            return "<#38BDF8>ᴄʀᴀғᴛɪᴏɴ ᴄɪғᴛᴄɪ";
        }
        return value;
    }

    private NamedTextColor parseTextColor(String value) {
        if (value == null || value.isBlank()) {
            return NamedTextColor.AQUA;
        }

        NamedTextColor color = NamedTextColor.NAMES.value(value.trim().toLowerCase(Locale.ROOT));
        return color == null ? NamedTextColor.AQUA : color;
    }

    private static Optional<ApiBinding> resolveBinding(FancyNpcsApiMode apiMode, DebugLogger debugLogger) {
        List<ApiPackage> packages = switch (apiMode) {
            case LEGACY -> List.of(LEGACY_API);
            case MODERN -> MODERN_APIS;
            case AUTO -> autoPackages();
        };

        for (ApiPackage apiPackage : packages) {
            Optional<ApiBinding> binding = bind(apiPackage);
            if (binding.isPresent()) {
                debugLogger.debug("FancyNPCs API binding selected: " + apiPackage.mode() + " " + apiPackage.pluginClassName());
                return binding;
            }
        }

        debugLogger.debug("FancyNPCs API binding unsupported for mode: " + apiMode);
        return Optional.empty();
    }

    private static List<ApiPackage> autoPackages() {
        List<ApiPackage> packages = new ArrayList<>(MODERN_APIS);
        packages.add(LEGACY_API);
        return packages;
    }

    private static Optional<ApiBinding> bind(ApiPackage apiPackage) {
        try {
            ClassLoader classLoader = FancyNpcsVisualProvider.class.getClassLoader();
            Class<?> pluginClass = Class.forName(apiPackage.pluginClassName(), false, classLoader);
            Class<?> npcDataClass = Class.forName(apiPackage.npcDataClassName(), false, classLoader);
            Class<?> skinDataClass = Class.forName(apiPackage.skinDataClassName(), false, classLoader);
            Class<?> npcClass = Class.forName(apiPackage.npcClassName(), false, classLoader);
            Constructor<?> npcDataConstructor = npcDataClass.getConstructor(
                String.class,
                String.class,
                UUID.class,
                String.class,
                skinDataClass,
                Location.class,
                boolean.class,
                boolean.class,
                boolean.class,
                boolean.class,
                NamedTextColor.class,
                EntityType.class,
                Map.class,
                boolean.class,
                int.class,
                Consumer.class,
                Map.class,
                float.class,
                float.class,
                int.class,
                Map.class,
                boolean.class
            );
            pluginClass.getMethod("get");
            pluginClass.getMethod("getNpcAdapter");
            pluginClass.getMethod("getNpcManager");
            return Optional.of(new ApiBinding(apiPackage.mode(), pluginClass, npcClass, npcDataConstructor));
        } catch (ReflectiveOperationException | LinkageError exception) {
            return Optional.empty();
        }
    }

    private String readableMessage(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message;
    }

    private record ApiPackage(
        FancyNpcsApiMode mode,
        String pluginClassName,
        String npcDataClassName,
        String skinDataClassName,
        String npcClassName
    ) {
    }

    private record ApiBinding(
        FancyNpcsApiMode mode,
        Class<?> pluginClass,
        Class<?> npcClass,
        Constructor<?> npcDataConstructor
    ) {
    }
}
