package ac.grim.grimac;

import ac.grim.grimac.api.platform.CoreLoader;
import ac.grim.grimac.api.platform.PlatformLoader;
import ac.grim.grimac.api.platform.init.Initable;

/**
 * Service-loader entry that just forwards every call to the
 * canonical singleton GrimAPI.INSTANCE.
 * Ensures that ServiceLoader call to clazz.getDeclaredConstructor().newInstance(); will not create two instances
 */
public final class GrimAPICoreLoader implements CoreLoader {

    @Override
    public void bootstrap(PlatformLoader platform, Initable... initables) {
        GrimAPI.INSTANCE.bootstrap(platform, initables);
    }

    @Override
    public void start() {
        GrimAPI.INSTANCE.start();
    }

    @Override
    public void stop() {
        GrimAPI.INSTANCE.stop();
    }
}
