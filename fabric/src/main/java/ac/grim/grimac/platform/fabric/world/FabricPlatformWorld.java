package ac.grim.grimac.platform.fabric.world;

import ac.grim.grimac.api.platform.world.PlatformChunk;
import ac.grim.grimac.api.platform.world.PlatformWorld;
import ac.grim.grimac.platform.fabric.GrimACFabricLoaderPlugin;
import lombok.Getter;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
public class FabricPlatformWorld implements PlatformWorld {

    private final ServerWorld fabricWorld;

    public FabricPlatformWorld(@NotNull ServerWorld world) {
        this.fabricWorld = world;
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return fabricWorld.isChunkLoaded(chunkX, chunkZ);
    }

//    @Override
//    public  getBlockAt(int x, int y, int z) {
//        return Block.getRawIdFromState(fabricWorld.getBlockState(new BlockPos(x, y, z)));
//    }

    @Override
    public boolean isAirAt(int x, int y, int z) {
        return fabricWorld.getBlockState(new BlockPos(x, y, z)).isAir();
    }

    @Override
    public String getName() {
        return fabricWorld.worldProperties.getLevelName();
    }

    @Override
    public @Nullable UUID getUID() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PlatformChunk getChunkAt(int currChunkX, int currChunkZ) {
        return new FabricPlatformChunk(fabricWorld.getChunk(currChunkX, currChunkZ));
    }

    @Override
    public boolean isLoaded() {
        return GrimACFabricLoaderPlugin.FABRIC_SERVER.getWorld(fabricWorld.getRegistryKey()) != null;
    }
}
