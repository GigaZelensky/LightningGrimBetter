package ac.grim.boar.anticheat.packets.other;

import ac.grim.boar.anticheat.check.api.Check;
import ac.grim.boar.anticheat.check.api.impl.PacketCheck;
import ac.grim.boar.protocol.event.CloudburstPacketEvent;
import ac.grim.boar.protocol.listener.PacketListener;

public class PacketCheckRunner implements PacketListener {
    @Override
    public void onPacketSend(final CloudburstPacketEvent event, final boolean immediate) {
        for (final Check check : event.getPlayer().getCheckHolder().values()) {
            if (!(check instanceof PacketCheck packetCheck)) {
                continue;
            }

            packetCheck.onPacketSend(event, immediate);
        }
    }

    @Override
    public void onPacketReceived(final CloudburstPacketEvent event) {
        for (final Check check : event.getPlayer().getCheckHolder().values()) {
            if (!(check instanceof PacketCheck packetCheck)) {
                continue;
            }

            packetCheck.onPacketReceived(event);
        }
    }
}
