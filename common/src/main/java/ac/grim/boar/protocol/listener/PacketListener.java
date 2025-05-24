package ac.grim.boar.protocol.listener;

import ac.grim.boar.protocol.event.CloudburstPacketEvent;

public interface PacketListener {
    default void onPacketSend(final CloudburstPacketEvent event, final boolean immediate) {
    }

    default void onPacketReceived(final CloudburstPacketEvent event) {
    }
}