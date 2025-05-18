package ac.grim.grimac.checks.impl.sprint;

import ac.grim.grimac.api.packet.types.client.play.ClientEntityActionPacket;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import ac.grim.grimac.api.packet.types.PacketTypes;

import static com.github.retrooper.packetevents.protocol.potion.PotionTypes.BLINDNESS;

@CheckData(name = "SprintD", description = "Started sprinting while having blindness", setback = 5, experimental = true)
public class SprintD extends Check implements PostPredictionCheck {
    public boolean startedSprintingBeforeBlind = false;

    public SprintD(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketTypes.Play.Client.ENTITY_ACTION) {
            if (packetFactory.clientEntityAction(event).getAction() == ClientEntityActionPacket.Action.START_SPRINTING) {
                startedSprintingBeforeBlind = false;
            }
        }
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (player.compensatedEntities.self.hasPotionEffect(BLINDNESS)) {
            if (player.isSprinting && !startedSprintingBeforeBlind) {
                flagAndAlertWithSetback();
            } else reward();
        }
    }
}
