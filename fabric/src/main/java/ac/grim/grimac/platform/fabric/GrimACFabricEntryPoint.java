package ac.grim.grimac.platform.fabric;

import ac.grim.grimac.api.platform.CoreLoader;
import ac.grim.grimac.platform.fabric.initables.FabricBStats;
import ac.grim.grimac.platform.fabric.initables.FabricTickEndEvent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.util.List;


public class GrimACFabricEntryPoint implements PreLaunchEntrypoint, ModInitializer {
    @Override
    public void onPreLaunch() {
    }

    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        String chainLoadEntryPointName = "grimMainLoad";

        // Collect grimMainLoad entrypoints and sort by version
        List<GrimACFabricLoaderPlugin> mainChainLoadEntryPoints = loader.getEntrypoints(chainLoadEntryPointName, GrimACFabricLoaderPlugin.class);
        mainChainLoadEntryPoints.sort((a, b) -> b.getNativeVersion().getProtocolVersion() - a.getNativeVersion().getProtocolVersion());

        // Get entrypoint for newest sub-version and execute it
        GrimACFabricLoaderPlugin platformLoader = mainChainLoadEntryPoints.get(0);
        GrimACFabricLoaderPlugin.LOADER = platformLoader;


        // On Fabric we have to register commands earlier, and cannot register them when server is no longer null
        CoreLoader.Manager.loadAll(
                platformLoader,
                new FabricBStats(),
                new FabricTickEndEvent()
        );

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            GrimACFabricLoaderPlugin.FABRIC_SERVER = server;     // if you cache it somewhere
            CoreLoader.Manager.startAll();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            CoreLoader.Manager.stopAll();
            platformLoader.getScheduler().shutdown();
        });
    }
}
