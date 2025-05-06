package ac.grim.grimac;

import ac.grim.grimac.api.event.EventBus;
import ac.grim.grimac.api.event.OptimizedEventBus;
import ac.grim.grimac.api.platform.CoreLoader;
import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.grim.grimac.manager.AlertManagerImpl;
import ac.grim.grimac.manager.DiscordManager;
import ac.grim.grimac.manager.InitManager;
import ac.grim.grimac.manager.SpectateManager;
import ac.grim.grimac.manager.TickManager;
import ac.grim.grimac.manager.config.BaseConfigManager;
import ac.grim.grimac.api.platform.init.Initable;
import ac.grim.grimac.api.platform.Platform;
import ac.grim.grimac.api.platform.PlatformLoader;
import ac.grim.grimac.api.platform.PlatformServer;
import ac.grim.grimac.api.platform.manager.ItemResetHandler;
import ac.grim.grimac.api.platform.manager.MessagePlaceHolderManager;
import ac.grim.grimac.api.platform.manager.ParserDescriptorFactory;
import ac.grim.grimac.api.platform.manager.PermissionRegistrationManager;
import ac.grim.grimac.api.platform.manager.PlatformPluginManager;
import ac.grim.grimac.api.platform.player.PlatformPlayerFactory;
import ac.grim.grimac.api.platform.scheduler.PlatformScheduler;
import ac.grim.grimac.api.platform.sender.Sender;
import ac.grim.grimac.api.platform.sender.SenderFactory;
import ac.grim.grimac.utils.anticheat.PlayerDataManager;
import ac.grim.grimac.api.reflection.ReflectionUtils;
import com.github.retrooper.packetevents.PacketEventsAPI;
import lombok.Getter;
import org.incendo.cloud.CommandManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
public final class GrimAPI implements CoreLoader {
    public final GrimAPI INSTANCE = new GrimAPI();

    @Getter
    private final Platform platform = detectPlatform();
    private final BaseConfigManager configManager;
    private final AlertManagerImpl alertManager;
    private final SpectateManager spectateManager;
    private final DiscordManager discordManager;
    private final PlayerDataManager playerDataManager;
    private final TickManager tickManager;
    private final EventBus eventBus;
    private final GrimExternalAPI externalAPI;
    private PlatformLoader loader;
    @Getter
    private InitManager initManager;
    private boolean initialized = false;

    public GrimAPI() {
        this.configManager = new BaseConfigManager();
        this.alertManager = new AlertManagerImpl(this);
        this.spectateManager = new SpectateManager(this);
        this.discordManager = new DiscordManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.tickManager = new TickManager();
        this.eventBus = new OptimizedEventBus();
        this.externalAPI = new GrimExternalAPI(this);
        System.out.println("GrimAPI classloader: " + GrimAPI.class.getClassLoader());
        System.out.println("GrimAPI instance: " + this);
    }

    private static Platform detectPlatform() {
        final Map<String, Platform> platforms = Map.of(
                "io.papermc.paper.threadedregions.RegionizedServer", Platform.FOLIA,
                "org.bukkit.Bukkit", Platform.BUKKIT,
                "net.fabricmc.loader.api.FabricLoader", Platform.FABRIC
        );

        return platforms.entrySet().stream()
                .filter(entry -> ReflectionUtils.hasClass(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown platform!"));
    }

    public void bootstrap(@NotNull PlatformLoader platformLoader, Initable... platformSpecificInitables) {
        this.loader = platformLoader;
        this.initManager = new InitManager(this, (PacketEventsAPI<?>) loader.getPacketAPI(), loader::getCommandManager, platformSpecificInitables);
        this.initManager.load();
        this.initialized = true;
    }

    public void start() {
        checkInitialized();
        initManager.start();
    }

    public void stop() {
        checkInitialized();
        initManager.stop();
    }

    public PlatformScheduler getScheduler() {
        return loader.getScheduler();
    }

    public PlatformPlayerFactory getPlatformPlayerFactory() {
        return loader.getPlatformPlayerFactory();
    }

    public ParserDescriptorFactory getParserDescriptors() {
        return loader.getParserDescriptorFactory();
    }

    public GrimPlugin getGrimPlugin() {
        return loader.getPlugin();
    }

    public SenderFactory<?> getSenderFactory() {
        return loader.getSenderFactory();
    }

    public ItemResetHandler getItemResetHandler() {
        return loader.getItemResetHandler();
    }

    public PlatformPluginManager getPluginManager() {
        return loader.getPluginManager();
    }

    public PlatformServer getPlatformServer() {
        return loader.getPlatformServer();
    }

    public @NotNull MessagePlaceHolderManager getMessagePlaceHolderManager() {
        return loader.getMessagePlaceHolderManager();
    }

    public CommandManager<Sender> getCommandManager() {
        return loader.getCommandManager();
    }

    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("GrimAPI has not been initialized!");
        }
    }

    public PermissionRegistrationManager getPermissionManager() {
        return loader.getPermissionManager();
    }

}
