package ac.grim.grimac.manager.init.stop;

import ac.grim.grimac.api.platform.init.StoppableInitable;
import ac.grim.grimac.api.util.LogUtil;
import com.github.retrooper.packetevents.PacketEvents;

public class TerminatePacketEvents implements StoppableInitable {
    @Override
    public void stop() {
        LogUtil.info("Terminating PacketEvents...");
        PacketEvents.getAPI().terminate();
    }
}
