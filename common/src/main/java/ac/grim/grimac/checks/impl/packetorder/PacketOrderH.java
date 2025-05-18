package ac.grim.grimac.checks.impl.packetorder;

import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import ac.grim.grimac.api.packet.types.PacketTypes;

@CheckData(name = "PacketOrderH", experimental = true)
public class PacketOrderH extends Check implements PostPredictionCheck {
    public PacketOrderH(final GrimPlayer player) {
        super(player);
    }

    private int invalid;

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketTypes.Play.Client.ENTITY_ACTION) {
            switch (packetFactory.clientEntityAction(event).getAction()) {
                case START_SPRINTING, STOP_SPRINTING -> {
                    if (player.getClientVersion().isOlderThan(PacketClientVersions.V_1_21_2) && player.packetOrderProcessor.isSneaking()) {
                        if (!player.canSkipTicks()) {
                            flagAndAlert();
                        } else {
                            invalid++;
                        }
                    }
                }
                case START_SNEAKING, STOP_SNEAKING -> {
                    if (player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_21_2) && player.packetOrderProcessor.isSprinting()) {
                        if (!player.canSkipTicks()) {
                            flagAndAlert();
                        } else {
                            invalid++;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!player.canSkipTicks()) return;

        if (player.isTickingReliablyFor(3)) {
            for (; invalid >= 1; invalid--) {
                flagAndAlert();
            }
        }

        invalid = 0;
    }
}
