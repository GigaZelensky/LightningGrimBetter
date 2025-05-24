package ac.grim.boar.anticheat.check.impl.timer;

import ac.grim.boar.anticheat.check.api.annotations.CheckInfo;
import ac.grim.boar.anticheat.check.api.impl.PacketCheck;
import ac.grim.boar.anticheat.player.BoarPlayer;
import ac.grim.boar.protocol.event.CloudburstPacketEvent;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

@CheckInfo(name = "Timer", type = "*")
public final class Timer extends PacketCheck {
    public Timer(final BoarPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceived(final CloudburstPacketEvent event) {
        if (!(event.getPacket() instanceof PlayerAuthInputPacket)) {
            return;
        }
    }
}
