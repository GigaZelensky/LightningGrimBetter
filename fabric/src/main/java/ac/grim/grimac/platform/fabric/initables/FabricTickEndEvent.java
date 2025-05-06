package ac.grim.grimac.platform.fabric.initables;

import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.GrimUser;
import ac.grim.grimac.api.platform.init.AbstractTickEndEvent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;


public class FabricTickEndEvent extends AbstractTickEndEvent {

    @Override
    public void start() {
        if (!super.shouldInjectEndTick()) {
            return;
        }

        // Register the end-of-tick callback
        ServerTickEvents.END_SERVER_TICK.register(this::onEndServerTick);
    }

    private void onEndServerTick(MinecraftServer server) {
        tickAllPlayers();
    }

    private void tickAllPlayers() {
        for (GrimUser player : GrimAPIProvider.getDirect().getGrimUsers()) {
            if (player.isGrimDisabled()) continue;
            player.onEndOfTick();
        }
    }
}
