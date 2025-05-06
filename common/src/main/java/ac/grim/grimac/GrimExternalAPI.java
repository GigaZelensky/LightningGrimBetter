package ac.grim.grimac;

import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.platform.Platform;
import ac.grim.grimac.api.platform.PlatformLoader;
import ac.grim.grimac.api.plugin.GrimPluginDescription;
import ac.grim.grimac.api.GrimUser;
import ac.grim.grimac.api.alerts.AlertManager;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.event.EventBus;
import ac.grim.grimac.api.event.events.GrimReloadEvent;
import ac.grim.grimac.manager.config.ConfigManagerFileImpl;
import ac.grim.grimac.api.platform.init.StartableInitable;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.api.util.LogUtil;
import ac.grim.grimac.api.util.ChatUtil;
import ac.grim.grimac.utils.common.ConfigReloadObserver;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import lombok.Getter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

//This is used for grim's external API. It has its own class just for organization.

public class GrimExternalAPI implements GrimAbstractAPI, ConfigReloadObserver, StartableInitable {

    private final GrimAPI api;
    @Getter
    private final Map<String, Function<GrimUser, String>> variableReplacements = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, String> staticReplacements = new ConcurrentHashMap<>();
    private final Map<String, Function<Object, Object>> functions = new ConcurrentHashMap<>();
    private final ConfigManagerFileImpl configManagerFile;
    private ConfigManager configManager = null;
    private boolean started = false;

    public GrimExternalAPI(GrimAPI api) {
        this.api = api;
        this.configManagerFile = new ConfigManagerFileImpl(api);
    }

    @Override
    public @NonNull EventBus getEventBus() {
        return api.getEventBus();
    }

    @Override
    public @Nullable GrimUser getGrimUser(UUID uuid) {
        return api.getPlayerDataManager().getPlayer(uuid);
    }

    @Override
    public Collection<GrimUser> getGrimUsers() {
        return (Collection) api.getPlayerDataManager().getEntries();
    }

    @Override
    public void registerVariable(String string, Function<GrimUser, String> replacement) {
        if (replacement == null) {
            variableReplacements.remove(string);
        } else {
            variableReplacements.put(string, replacement);
        }
    }

    @Override
    public void registerVariable(String variable, String replacement) {
        if (replacement == null) {
            staticReplacements.remove(variable);
        } else {
            staticReplacements.put(variable, replacement);
        }
    }

    @Override
    public String getGrimVersion() {
        GrimPluginDescription description = api.getGrimPlugin().getDescription();
        return description.getVersion();
    }

    @Override
    public void registerFunction(String key, Function<Object, Object> function) {
        if (function == null) {
            functions.remove(key);
        } else {
            functions.put(key, function);
        }
    }

    @Override
    public Function<Object, Object> getFunction(String key) {
        return functions.get(key);
    }

    @Override
    public AlertManager getAlertManager() {
        return api.getAlertManager();
    }

    @Override
    public ConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public boolean hasStarted() {
        return started;
    }

    @Override
    public int getCurrentTick() {
        return api.getTickManager().currentTick;
    }

    @Override
    public PlatformLoader getPlatformLoader() {
        return api.getLoader();
    }

    @Override
    public Platform getPlatform() {
        return api.getPlatform();
    }

    // on load, load the config & register the service
    public void load() {
        reload(configManagerFile);
        api.getLoader().registerAPIService();
    }

    // handles any config loading that's needed to be done after load
    @Override
    public void start() {
        started = true;
        try {
            api.getConfigManager().start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void reload(ConfigManager config) {
        if (config.isLoadedAsync() && started) {
            api.getScheduler().getAsyncScheduler().runNow(api.getGrimPlugin(),
                    () -> successfulReload(config));
        } else {
            successfulReload(config);
        }
    }

    @Override
    public CompletableFuture<Boolean> reloadAsync(ConfigManager config) {
        if (config.isLoadedAsync() && started) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            api.getScheduler().getAsyncScheduler().runNow(api.getGrimPlugin(),
                    () -> future.complete(successfulReload(config)));
            return future;
        }
        return CompletableFuture.completedFuture(successfulReload(config));
    }

    private boolean successfulReload(ConfigManager config) {
        try {
            config.reload();
            api.getConfigManager().load(config);
            if (started) api.getConfigManager().start();
            onReload(config);
            if (started)
                api.getScheduler().getAsyncScheduler().runNow(api.getGrimPlugin(),
                        () -> api.getEventBus().post(new GrimReloadEvent(true)));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (started)
            api.getScheduler().getAsyncScheduler().runNow(api.getGrimPlugin(),
                    () -> api.getEventBus().post(new GrimReloadEvent(false)));
        return false;
    }

    @Override
    public void onReload(ConfigManager newConfig) {
        if (newConfig == null) {
            LogUtil.warn("ConfigManager not set. Using default config file manager.");
            configManager = configManagerFile;
        } else {
            configManager = newConfig;
        }
        // Update variables
        updateVariables();
        // Restart
        api.getAlertManager().reload(configManager);
        api.getDiscordManager().reload();
        api.getSpectateManager().reload();
        // Don't reload players if the plugin hasn't started yet
        if (!started) return;
        // Reload checks for all players
        for (GrimPlayer grimPlayer : api.getPlayerDataManager().getEntries()) {
            ChannelHelper.runInEventLoop(grimPlayer.user.getChannel(), () -> {
                grimPlayer.updatePermissions();
                grimPlayer.reload(configManager);
            });
        }
    }

    private void updateVariables() {
        variableReplacements.putIfAbsent("%player%", GrimUser::getName);
        variableReplacements.putIfAbsent("%uuid%", user -> user.getUniqueId().toString());
        variableReplacements.putIfAbsent("%ping%", user -> user.getTransactionPing() + "");
        variableReplacements.putIfAbsent("%brand%", GrimUser::getBrand);
        variableReplacements.putIfAbsent("%h_sensitivity%", user -> ((int) Math.round(user.getHorizontalSensitivity() * 200)) + "");
        variableReplacements.putIfAbsent("%v_sensitivity%", user -> ((int) Math.round(user.getVerticalSensitivity() * 200)) + "");
        variableReplacements.putIfAbsent("%fast_math%", user -> !user.isVanillaMath() + "");
        variableReplacements.putIfAbsent("%tps%", user -> String.format("%.2f", api.getPlatformServer().getTPS()));
        variableReplacements.putIfAbsent("%version%", GrimUser::getVersionName);
        // static variables
        staticReplacements.put("%prefix%", ChatUtil.translateAlternateColorCodes('&', api.getConfigManager().getPrefix()));
        staticReplacements.putIfAbsent("%grim_version%", getGrimVersion());
    }
}
