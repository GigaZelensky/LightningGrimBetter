package ac.grim.grimac.platform.fabric.initables;

import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.platform.init.StartableInitable;
import ac.grim.grimac.api.platform.init.StoppableInitable;
import ac.grim.grimac.platform.fabric.utils.metrics.MetricsFabric;

public class FabricBStats implements StartableInitable, StoppableInitable {

    private MetricsFabric metricsFabric;

    @Override
    public void start() {
        int pluginId = 12820; // <-- Replace with the id of your plugin!
        try {
            metricsFabric = new MetricsFabric(GrimAPIProvider.getDirect().getPlatformLoader().getPlugin(), pluginId);
        } catch (Exception ignored) {}
    }

    @Override
    public void stop() {
        if (metricsFabric != null)
            metricsFabric.shutdown();
    }
}
