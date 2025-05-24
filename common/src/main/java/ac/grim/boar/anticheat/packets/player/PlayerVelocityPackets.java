package ac.grim.boar.anticheat.packets.player;

import ac.grim.boar.anticheat.data.input.VelocityData;
import ac.grim.boar.anticheat.player.BoarPlayer;
import ac.grim.boar.anticheat.util.math.Vec3;
import ac.grim.boar.protocol.event.CloudburstPacketEvent;
import ac.grim.boar.protocol.listener.PacketListener;
import org.cloudburstmc.protocol.bedrock.data.MovementEffectType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.MovementEffectPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket;

public class PlayerVelocityPackets implements PacketListener {
    @Override
    public void onPacketSend(final CloudburstPacketEvent event, final boolean immediate) {
        final BoarPlayer player = event.getPlayer();

        // Yes only this, there no packet for explosion (for bedrock), geyser translate explosion directly to SetEntityMotionPacket
        if (event.getPacket() instanceof SetEntityMotionPacket packet) {
            if (packet.getRuntimeEntityId() != player.runtimeEntityId) {
                return;
            }

            player.sendLatencyStack(immediate);
            player.queuedVelocities.put(player.sentStackId.get() + 1, new VelocityData(player.sentStackId.get() + 1, player.tick, new Vec3(packet.getMotion())));
            event.getPostTasks().add(player::sendLatencyStack);
        }

        if (event.getPacket() instanceof MovementEffectPacket packet) {
            if (packet.getEntityRuntimeId() != player.runtimeEntityId || packet.getEffectType() != MovementEffectType.GLIDE_BOOST) {
                return;
            }

            player.glideBoostTicks = player.getFlagTracker().has(EntityFlag.GLIDING) ? packet.getDuration() / 2 : 0;
        }
    }
}
