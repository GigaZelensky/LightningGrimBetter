package ac.grim.grimac.platform.fabric.utils.thread;

import ac.grim.grimac.api.GrimAPIProvider;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class FabricFutureUtil {
    public static <U> CompletableFuture<U> supplySync(Supplier<U> entityTeleportSupplier) {
        CompletableFuture<U> ret = new CompletableFuture<>();
        GrimAPIProvider.getDirect().getPlatformLoader().getScheduler().getGlobalRegionScheduler().run(GrimAPIProvider.getDirect().getPlatformLoader().getPlugin(),
                () -> ret.complete(entityTeleportSupplier.get()));
        return ret;
    }
}
