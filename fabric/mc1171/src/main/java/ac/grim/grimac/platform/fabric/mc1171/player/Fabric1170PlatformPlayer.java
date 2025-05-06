package ac.grim.grimac.platform.fabric.mc1171.player;

import ac.grim.grimac.platform.fabric.mc1161.player.Fabric1161PlatformPlayer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;


public class Fabric1170PlatformPlayer extends Fabric1161PlatformPlayer {
    public Fabric1170PlatformPlayer(ServerPlayerEntity player) {
        super(player);
    }

    @Override
    public void setGameMode(int gameModeID) {
        fabricPlayer.changeGameMode(GameMode.byId(gameModeID));
    }
}
