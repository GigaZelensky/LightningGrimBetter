package ac.grim.grimac.platform.bukkit.initables;

import ac.grim.grimac.api.platform.init.StartableInitable;
import ac.grim.grimac.api.util.LogUtil;
import ac.grim.grimac.platform.bukkit.GrimACBukkitLoaderPlugin;
import ac.grim.grimac.platform.bukkit.events.PistonEvent;
import org.bukkit.Bukkit;

public class BukkitEventManager implements StartableInitable {
    public void start() {
        LogUtil.info("Registering singular bukkit event... (PistonEvent)");

        Bukkit.getPluginManager().registerEvents(new PistonEvent(), GrimACBukkitLoaderPlugin.LOADER);
    }
}
