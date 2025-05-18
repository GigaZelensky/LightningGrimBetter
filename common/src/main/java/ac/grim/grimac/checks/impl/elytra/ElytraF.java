package ac.grim.grimac.checks.impl.elytra;

import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.types.PacketTypes;
import ac.grim.grimac.api.packet.types.client.play.ClientEntityActionPacket;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;

@CheckData(name = "ElytraF", description = "Started gliding while on ground", experimental = true)
public class ElytraF extends Check implements PostPredictionCheck {
    private boolean setback;

    public ElytraF(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (player.getClientVersion().isOlderThanOrEquals(PacketClientVersions.V_1_8)) {
            return;
        }

        if (event.getPacketType() == PacketTypes.Play.Client.ENTITY_ACTION
                && packetFactory.clientEntityAction(event).getAction() == ClientEntityActionPacket.Action.START_FLYING_WITH_ELYTRA
                && player.clientClaimsLastOnGround
                && flagAndAlert()
        ) {
            setback = true;
            if (shouldModifyPackets()) {
                event.setCancelled(true);
                player.onPacketCancel();
                player.resyncPose();
            }
        }
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (setback) {
            setbackIfAboveSetbackVL();
            setback = false;
        }
    }
}
