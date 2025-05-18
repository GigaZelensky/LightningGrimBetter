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

@CheckData(name = "ElytraA", description = "Started gliding while already gliding", experimental = true)
public class ElytraA extends Check implements PostPredictionCheck {
    private boolean setback;

    public ElytraA(GrimPlayer player) {
        super(player);
    }

    public void onStartGliding(PacketReceiveEvent event) {
        if (player.getClientVersion().isOlderThanOrEquals(PacketClientVersions.V_1_8)) {
            return;
        }

        if (player.isGliding && flagAndAlert()) {
            setback = true;
            if (shouldModifyPackets()) {
                event.setCancelled(true);
                player.onPacketCancel();
                player.resyncPose();
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (player.getClientVersion().isOlderThan(PacketClientVersions.V_1_15) && event.getPacketType() == PacketTypes.Play.Client.ENTITY_ACTION
                && packetFactory.clientEntityAction(event).getAction() == ClientEntityActionPacket.Action.START_FLYING_WITH_ELYTRA
        ) onStartGliding(event);
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (setback) {
            setbackIfAboveSetbackVL();
            setback = false;
        }
    }
}
