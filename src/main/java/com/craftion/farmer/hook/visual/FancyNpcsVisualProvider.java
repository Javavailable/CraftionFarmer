package com.craftion.farmer.hook.visual;

import com.craftion.farmer.config.ConfigManager;
import com.craftion.farmer.debug.DebugLogger;
import com.craftion.farmer.farmer.Farmer;
import com.craftion.farmer.farmer.LocationSnapshot;
import com.craftion.farmer.scheduler.SchedulerAdapter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
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

    private static final String FANCY_NPCS_PLUGIN_CLASS = "de.oliver.fancynpcs.api.FancyNpcsPlugin";
    private static final String NPC_DATA_CLASS = "de.oliver.fancynpcs.api.NpcData";
    private static final String SKIN_DATA_CLASS = "de.oliver.fancynpcs.api.skins.SkinData";

    private final JavaPlugin plugin;
    private final SchedulerAdapter schedulerAdapter;
    private final DebugLogger debugLogger;
    private final FancyNpcsApiMode apiMode;
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

    public FancyNpcsVisualProvider(
        JavaPlugin plugin,
        ConfigManager configManager,
        SchedulerAdapter schedulerAdapter,
        DebugLogger debugLogger
    ) {
        this.plugin = plugin;
        this.schedulerAdapter = schedulerAdapter;
        this.debugLogger = debugLogger;
        this.apiMode = FancyNpcsApiMode.from(configManager.fancyNpcsApiMode());
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

        Optional<Location> location = location(farmer.location());
        if (location.isEmpty()) {
            this.debugLogger.debug("FancyNPCs spawn skipped because world is not loaded: " + farmer.farmerId());
            return;
        }

        this.schedulerAdapter.runAtLocation(location.get(), () -> spawnNow(farmer, location.get()));
    }

    @Override
    public void remove(Farmer farmer) {
        if (farmer != null) {
            remove(farmer.farmerId());
        }
    }

    @Override
    public void remove(String farmerId) {
        if (farmerId == null || farmerId.isBlank() || !this.removeOnFarmerDelete) {
            return;
        }

        this.schedulerAdapter.runGlobal(() -> removeNpcById(npcId(farmerId), false));
    }

    @Override
    public void reconcile(Collection<Farmer> farmers) {
        if (farmers == null) {
            return;
        }

        this.schedulerAdapter.runGlobal(() -> removeStaleNpcs(farmers));
        for (Farmer farmer : farmers) {
            spawn(farmer);
        }
    }

    @Override
    public void shutdown() {
        if (!this.saveNpcsToFile) {
            this.schedulerAdapter.runGlobal(this::removeAllManagedNpcs);
        }
    }

    private void spawnNow(Farmer farmer, Location location) {
        String npcId = npcId(farmer.farmerId());
        try {
            Object manager = npcManager();
            Object existingNpc = invoke(manager, "getNpcById", new Class<?>[] {String.class}, npcId);
            if (existingNpc != null) {
                removeNpc(existingNpc, true);
            }

            Object npcData = createNpcData(npcId, farmer, location);
            Object pluginApi = fancyNpcsPlugin();
            @SuppressWarnings("unchecked")
            Function<Object, Object> adapter = (Function<Object, Object>) invoke(pluginApi, "getNpcAdapter");
            Object npc = adapter.apply(npcData);
            invokeIfPresent(npc, "setSaveToFile", new Class<?>[] {boolean.class}, this.saveNpcsToFile);
            Class<?> npcClass = Class.forName("de.oliver.fancynpcs.api.Npc");
            invoke(manager, "registerNpc", new Class<?>[] {npcClass}, npc);
            invoke(npc, "create");
            invoke(npc, "spawnForAll");
            saveNpcsIfNeeded(manager);
            this.debugLogger.debug("FancyNPCs farmer npc spawned: " + npcId + " mode=" + this.apiMode);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            this.plugin.getLogger().warning("FancyNPCs farmer NPC olusturulamadi: " + readableMessage(exception));
        }
    }

    private Object createNpcData(String npcId, Farmer farmer, Location location) {
        try {
            Class<?> npcDataClass = Class.forName(NPC_DATA_CLASS);
            Class<?> skinDataClass = Class.forName(SKIN_DATA_CLASS);
            Constructor<?> constructor = npcDataClass.getConstructor(
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

            Consumer<Player> onClick = player -> this.schedulerAdapter.runGlobal(() -> player.performCommand("farmer info"));
            return constructor.newInstance(
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
                removeNpc(npc, true);
            }
        }
    }

    private void removeAllManagedNpcs() {
        for (Object npc : allNpcs()) {
            String npcId = npcId(npc).orElse(null);
            if (npcId != null && npcId.startsWith(this.idPrefix)) {
                removeNpc(npc, true);
            }
        }
    }

    private void removeNpcById(String npcId, boolean force) {
        try {
            Object manager = npcManager();
            Object npc = invoke(manager, "getNpcById", new Class<?>[] {String.class}, npcId);
            if (npc == null) {
                return;
            }
            removeNpc(npc, force);
        } catch (RuntimeException | LinkageError exception) {
            this.plugin.getLogger().warning("FancyNPCs farmer NPC silinemedi: " + readableMessage(exception));
        }
    }

    private void removeNpc(Object npc, boolean force) {
        try {
            if (!force && !this.removeOnFarmerDelete) {
                return;
            }

            Object manager = npcManager();
            invoke(npc, "removeForAll");
            Class<?> npcClass = Class.forName("de.oliver.fancynpcs.api.Npc");
            invoke(manager, "removeNpc", new Class<?>[] {npcClass}, npc);
            saveNpcsIfNeeded(manager);
        } catch (ReflectiveOperationException exception) {
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
            Class<?> pluginClass = Class.forName(FANCY_NPCS_PLUGIN_CLASS);
            return pluginClass.getMethod("get").invoke(null);
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

    private String readableMessage(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message;
    }
}
