package ac.grim.grimac.checks.type;

import ac.grim.grimac.api.AbstractCheck;
import ac.grim.grimac.events.packets.registry.PacketHandlerRegistry;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;

public interface PacketCheck extends AbstractCheck {
    default void onPacketReceive(final PacketHandlerRegistry<PacketReceiveEvent> event) {
    }

    default void onPacketSend(final PacketHandlerRegistry<PacketSendEvent> event) {
    }
}
