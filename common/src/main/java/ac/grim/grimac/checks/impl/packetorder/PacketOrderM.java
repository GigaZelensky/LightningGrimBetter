package ac.grim.grimac.checks.impl.packetorder;

import ac.grim.grimac.api.packet.MCPacket;
import ac.grim.grimac.api.packet.types.PacketTypes;
import ac.grim.grimac.api.packet.world.enums.BlockFace;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

@CheckData(name = "PacketOrderM", experimental = true)
public class PacketOrderM extends Check implements PostPredictionCheck {
    public PacketOrderM(final GrimPlayer player) {
        super(player);
    }

    private int invalid;
    private boolean usingWithoutInteract, interacting;

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketTypes.Play.Client.INTERACT_ENTITY) {
            if (new WrapperPlayClientInteractEntity(event).getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                interacting = true;
                if (usingWithoutInteract) {
                    if (!player.canSkipTicks()) {
                        if (flagAndAlert() && shouldModifyPackets()) {
                            event.setCancelled(true);
                            player.onPacketCancel();
                        }
                    } else {
                        invalid++;
                    }
                }
            }
        }

        if (event.getPacketType() == PacketTypes.Play.Client.USE_ITEM
                || event.getPacketType() == PacketTypes.Play.Client.PLAYER_BLOCK_PLACEMENT
                && MCPacket.getAPI().packetFactory().newClientPlayerBlockPlacementPacket(event).blockFace() == BlockFace.OTHER) {
            if (!interacting) {
                usingWithoutInteract = true;
            }

            interacting = false;
        }

        if (player.gamemode == GameMode.SPECTATOR || isTickPacket(event.getPacketType())) {
            usingWithoutInteract = interacting = false;
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
