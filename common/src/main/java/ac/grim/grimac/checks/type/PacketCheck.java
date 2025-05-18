package ac.grim.grimac.checks.type;

import ac.grim.grimac.api.AbstractCheck;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import ac.grim.grimac.api.packet.types.event.PacketSendEvent;

public interface PacketCheck extends AbstractCheck {
    default void onPacketReceive(final PacketReceiveEvent event) {
    }

    default void onPacketSend(final PacketSendEvent event) {
    }
}
