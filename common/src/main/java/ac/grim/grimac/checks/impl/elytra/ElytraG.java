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
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;

@CheckData(name = "ElytraG", description = "Started gliding with levitation", experimental = true)
public class ElytraG extends Check implements PostPredictionCheck {
    private boolean setback;

    public ElytraG(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketTypes.Play.Client.ENTITY_ACTION
                && packetFactory.clientEntityAction(event).getAction() == ClientEntityActionPacket.Action.START_FLYING_WITH_ELYTRA
                && player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_16)
                && player.compensatedEntities.self.hasPotionEffect(PotionTypes.LEVITATION)
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
