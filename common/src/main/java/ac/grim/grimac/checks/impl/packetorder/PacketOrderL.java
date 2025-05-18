package ac.grim.grimac.checks.impl.packetorder;

import ac.grim.grimac.api.packet.types.PacketTypes;
import ac.grim.grimac.api.packet.types.client.play.ClientStatusPacket;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import ac.grim.grimac.api.packet.player.enums.DiggingAction;

import java.util.ArrayDeque;

@CheckData(name = "PacketOrderL", experimental = true)
public class PacketOrderL extends Check implements PostPredictionCheck {
    public PacketOrderL(final GrimPlayer player) {
        super(player);
    }

    private final ArrayDeque<String> flags = new ArrayDeque<>();

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketTypes.Play.Client.CLIENT_STATUS) {
            if (packetFactory.clientStatus(event).getClientStatusAction() == ClientStatusPacket.Action.OPEN_INVENTORY_ACHIEVEMENT) {
                if (player.packetOrderProcessor.isDropping()) {
                    if (!player.canSkipTicks()) {
                        if (flagAndAlert("inventory") && shouldModifyPackets()) {
                            event.setCancelled(true);
                            player.onPacketCancel();
                        }
                    } else {
                        flags.add("inventory");
                    }
                }
            }
        }

        if (event.getPacketType() == PacketTypes.Play.Client.PLAYER_DIGGING) {
            if (packetFactory.clientPlayerDigging(event).getDiggingAction() == DiggingAction.SWAP_ITEM_WITH_OFFHAND) {
                if (player.packetOrderProcessor.isDropping()) {
                    if (!player.canSkipTicks()) {
                        if (flagAndAlert("swap") && shouldModifyPackets()) {
                            event.setCancelled(true);
                            player.onPacketCancel();
                        }
                    } else {
                        flags.add("swap");
                    }
                }
            }
        }
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!player.canSkipTicks()) return;

        if (player.isTickingReliablyFor(3)) {
            for (String verbose : flags) {
                flagAndAlert(verbose);
            }
        }

        flags.clear();
    }
}
