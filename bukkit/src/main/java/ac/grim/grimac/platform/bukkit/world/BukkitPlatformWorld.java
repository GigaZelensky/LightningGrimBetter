package ac.grim.grimac.platform.bukkit.world;

import ac.grim.grimac.api.platform.world.PlatformChunk;
import ac.grim.grimac.api.platform.world.PlatformWorld;
import ac.grim.grimac.api.packet.block.PacketBlockState;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
public class BukkitPlatformWorld implements PlatformWorld {

    private final World bukkitWorld;

    public BukkitPlatformWorld(@NotNull World world) {
        this.bukkitWorld = world;
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return bukkitWorld.isChunkLoaded(chunkX, chunkZ);
    }

//    @Override
//    public  getBlockAt(int x, int y, int z) {
//        return fromBukkitBlockData(bukkitWorld.getBlockAt(x, y, z).getBlockData());
//    }

    @Override
    public boolean isAirAt(int x, int y, int z) {
        PacketBlockState wrappedBlockState = SpigotConversionUtil.fromBukkitBlockData(bukkitWorld.getBlockData(x, y , z));
        return wrappedBlockState.getType().isAir();
    }

    @Override
    public String getName() {
        return bukkitWorld.getName();
    }

    @Override
    public @Nullable UUID getUID() {
        return this.bukkitWorld.getUID();
    }

    @Override
    public PlatformChunk getChunkAt(int currChunkX, int currChunkZ) {
        return new BukkitPlatformChunk(bukkitWorld.getChunkAt(currChunkX, currChunkZ));
    }

    @Override
    public boolean isLoaded() {
        return Bukkit.getWorld(bukkitWorld.getUID()) != null;
    }
}
