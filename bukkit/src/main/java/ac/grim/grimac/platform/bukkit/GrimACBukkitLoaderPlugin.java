package ac.grim.grimac.platform.bukkit;

import ac.grim.grimac.api.lazy.LazyHolder;
import ac.grim.grimac.api.packet.MCPacketAPI;
import ac.grim.grimac.api.packet.impl.pe.PEPacketAPI;
import ac.grim.grimac.api.platform.CoreLoader;
import ac.grim.grimac.api.platform.init.Initable;
import ac.grim.grimac.api.plugin.BasicGrimPlugin;
import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.grim.grimac.api.platform.init.StartableInitable;
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
import ac.grim.grimac.platform.bukkit.initables.BukkitBStats;
import ac.grim.grimac.platform.bukkit.initables.BukkitEventManager;
import ac.grim.grimac.platform.bukkit.initables.BukkitTickEndEvent;
import ac.grim.grimac.platform.bukkit.manager.BukkitItemResetHandler;
import ac.grim.grimac.platform.bukkit.manager.BukkitMessagePlaceHolderManager;
import ac.grim.grimac.platform.bukkit.manager.BukkitParserDescriptorFactory;
import ac.grim.grimac.platform.bukkit.manager.BukkitPermissionRegistrationManager;
import ac.grim.grimac.platform.bukkit.manager.BukkitPlatformPluginManager;
import ac.grim.grimac.platform.bukkit.player.BukkitPlatformPlayerFactory;
import ac.grim.grimac.platform.bukkit.scheduler.bukkit.BukkitPlatformScheduler;
import ac.grim.grimac.platform.bukkit.scheduler.folia.FoliaPlatformScheduler;
import ac.grim.grimac.platform.bukkit.sender.BukkitSenderFactory;
import ac.grim.grimac.platform.bukkit.utils.placeholder.PlaceholderAPIExpansion;
import com.github.retrooper.packetevents.PacketEventsAPI;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.brigadier.BrigadierSetting;
import org.incendo.cloud.brigadier.CloudBrigadierManager;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import org.incendo.cloud.setting.Configurable;
import org.jetbrains.annotations.NotNull;

public final class GrimACBukkitLoaderPlugin extends JavaPlugin implements PlatformLoader {

    public static GrimACBukkitLoaderPlugin LOADER;

    private final LazyHolder<PlatformScheduler> scheduler = LazyHolder.simple(this::createScheduler);
    private final LazyHolder<PacketEventsAPI<?>> packetEvents = LazyHolder.simple(() -> SpigotPacketEventsBuilder.build(this));
    private final LazyHolder<BukkitSenderFactory> senderFactory = LazyHolder.simple(BukkitSenderFactory::new);
    private final LazyHolder<CommandManager<Sender>> commandManager = LazyHolder.simple(this::createCommandManager);
    private final LazyHolder<ItemResetHandler> itemResetHandler = LazyHolder.simple(BukkitItemResetHandler::new);

    private final PlatformPlayerFactory playerFactory = new BukkitPlatformPlayerFactory();
    private final ParserDescriptorFactory parserFactory = new BukkitParserDescriptorFactory();
    private final PlatformPluginManager platformPluginManager = new BukkitPlatformPluginManager();
    private final GrimPlugin plugin;
    private final PlatformServer platformServer = new BukkitPlatformServer();
    private final MessagePlaceHolderManager messagePlaceHolderManager = new BukkitMessagePlaceHolderManager();
    private final BukkitPermissionRegistrationManager bukkitPermissionRegistrationManager = new BukkitPermissionRegistrationManager();
    private final MCPacketAPI mcPacketAPI = new PEPacketAPI();

    public GrimACBukkitLoaderPlugin() {
        this.plugin = new BasicGrimPlugin(
                this.getLogger(),
                this.getDataFolder(),
                this.getDescription().getVersion(),
                this.getDescription().getDescription(),
                this.getDescription().getAuthors()
        );
    }

    @Override
    public void onLoad() {
        LOADER = this;
        CoreLoader.Manager.loadAll(this, this.getBukkitInitTasks());
    }

    private Initable[] getBukkitInitTasks() {
        return new Initable[] {
                new BukkitEventManager(),
                new BukkitTickEndEvent(),
                new BukkitBStats(),
                (StartableInitable) () -> {
                    if (BukkitMessagePlaceHolderManager.hasPlaceholderAPI) {
                        new PlaceholderAPIExpansion().register();
                    }
                }
        };
    }

    @Override
    public void onEnable() {
        CoreLoader.Manager.startAll();
    }

    @Override
    public void onDisable() {
        CoreLoader.Manager.stopAll();
    }

    @Override
    public PlatformScheduler getScheduler() {
        return scheduler.get();
    }

    @Override
    public PlatformPlayerFactory getPlatformPlayerFactory() {
        return playerFactory;
    }

    @Override
    public ParserDescriptorFactory getParserDescriptorFactory() {
        return parserFactory;
    }

    @Override
    public PacketEventsAPI<?> getPacketAPI() {
        return packetEvents.get();
    }

    @Override
    public CommandManager<Sender> getCommandManager() {
        return commandManager.get();
    }

    @Override
    public ItemResetHandler getItemResetHandler() {
        return itemResetHandler.get();
    }

    @Override
    public SenderFactory<CommandSender> getSenderFactory() {
        return senderFactory.get();
    }

    @Override
    public GrimPlugin getPlugin() {
        return plugin;
    }

    @Override
    public PlatformPluginManager getPluginManager() {
        return platformPluginManager;
    }

    @Override
    public PlatformServer getPlatformServer() {
        return platformServer;
    }

    @Override
    public void registerAPIService(GrimAbstractAPI api) {
        GrimAPIProvider.init(api);
        Bukkit.getServicesManager().register(GrimAbstractAPI.class, api, GrimACBukkitLoaderPlugin.LOADER, ServicePriority.Normal);
    }

    @Override
    public @NotNull MessagePlaceHolderManager getMessagePlaceHolderManager() {
        return messagePlaceHolderManager;
    }

    @Override
    public PermissionRegistrationManager getPermissionManager() {
        return bukkitPermissionRegistrationManager;
    }

    @Override
    public MCPacketAPI getMCPacketAPI() {
        return mcPacketAPI;
    }

    private PlatformScheduler createScheduler() {
        return GrimAPIProvider.getDirect().getPlatform() == Platform.FOLIA ? new FoliaPlatformScheduler() : new BukkitPlatformScheduler();
    }

    private CommandManager<Sender> createCommandManager() {
        LegacyPaperCommandManager<Sender> manager = new LegacyPaperCommandManager<>(
                this,
                ExecutionCoordinator.simpleCoordinator(),
                senderFactory.get()
        );
        if (manager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
            manager.registerBrigadier();
            CloudBrigadierManager<Sender, ?> cbm = manager.brigadierManager();
            Configurable<BrigadierSetting> settings = cbm.settings();
            settings.set(BrigadierSetting.FORCE_EXECUTABLE, true);
        } else if (manager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            manager.registerAsynchronousCompletions();
        }
        return manager;
    }

    public BukkitSenderFactory getBukkitSenderFactory() {
        return LOADER.senderFactory.get();
    }
}
