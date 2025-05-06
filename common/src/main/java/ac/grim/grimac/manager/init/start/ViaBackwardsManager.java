package ac.grim.grimac.manager.init.start;

import ac.grim.grimac.api.platform.init.StartableInitable;

public class ViaBackwardsManager implements StartableInitable {
    @Override
    public void start() {
        System.setProperty("com.viaversion.handlePingsAsInvAcknowledgements", "true");
    }
}
