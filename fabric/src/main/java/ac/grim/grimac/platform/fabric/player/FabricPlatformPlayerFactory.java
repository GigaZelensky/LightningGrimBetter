package ac.grim.grimac.platform.fabric.player;

import ac.grim.grimac.api.platform.entity.GrimEntity;
import ac.grim.grimac.api.platform.player.AbstractPlatformPlayerFactory;
import ac.grim.grimac.platform.fabric.GrimACFabricLoaderPlugin;
import net.minecraft.entity.Entity;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Function;

public class FabricPlatformPlayerFactory extends AbstractPlatformPlayerFactory<ServerPlayerEntity> {

    private final Function<ServerPlayerEntity, AbstractFabricPlatformPlayer> getPlayerFunction;
    private final Function<Entity, GrimEntity> getEntityFunction;
    private final Function<ServerPlayerEntity, AbstractFabricPlatformInventory> getPlayerInventoryFunction;

    public FabricPlatformPlayerFactory(Function<ServerPlayerEntity, AbstractFabricPlatformPlayer> playerSupplier,
                                       Function<Entity, GrimEntity> getEntityFunction,
                                       Function<ServerPlayerEntity, AbstractFabricPlatformInventory> getInventoryFunction
    ) {
        this.getPlayerFunction = playerSupplier;
        this.getEntityFunction = getEntityFunction;
        this.getPlayerInventoryFunction = getInventoryFunction;
    }

    @Override
    protected ServerPlayerEntity getNativePlayer(@NotNull UUID uuid) {
        return GrimACFabricLoaderPlugin.FABRIC_SERVER.getPlayerManager().getPlayer(uuid);
    }

    @Override
    protected AbstractFabricPlatformPlayer createPlatformPlayer(@NotNull ServerPlayerEntity nativePlayer) {
        return getPlayerFunction.apply(nativePlayer);
    }

    @Override
    protected boolean isNativePlayerType(@NotNull Object playerObject) {
        return playerObject instanceof ServerPlayerEntity;
    }

    @Override
    protected UUID getPlayerUUID(@NotNull ServerPlayerEntity nativePlayer) {
        return nativePlayer.getUuid();
    }

    @Override
    protected Class<ServerPlayerEntity> getNativePlayerClass() {
        return ServerPlayerEntity.class;
    }

    @Override
    protected Collection<ServerPlayerEntity> getNativeOnlinePlayers() {
        PlayerManager playerManager = GrimACFabricLoaderPlugin.FABRIC_SERVER.getPlayerManager();

        // Get the list of online players from the server
        return playerManager != null ? playerManager.getPlayerList() : Collections.emptyList();
    }

    @Override
    public void replaceNativePlayer(@NonNull UUID uuid, @NonNull ServerPlayerEntity serverPlayerEntity) {
        super.cache.getPlayer(uuid).replaceNativePlayer(serverPlayerEntity);
    }

    public AbstractFabricPlatformInventory getPlatformInventory(ServerPlayerEntity serverPlayerEntity) {
        return getPlayerInventoryFunction.apply(serverPlayerEntity);
    }

    public GrimEntity getPlatformEntity(Entity entity) {
        return getEntityFunction.apply(entity);
    }
}
