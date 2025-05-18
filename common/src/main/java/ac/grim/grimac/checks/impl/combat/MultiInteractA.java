package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import ac.grim.grimac.api.packet.types.PacketTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import java.util.ArrayList;

@CheckData(name = "MultiInteractA", description = "Interacted with multiple entities in the same tick", experimental = true)
public class MultiInteractA extends Check implements PostPredictionCheck {
    private final ArrayList<String> flags = new ArrayList<>();
    private int lastEntity;
    private boolean lastSneaking;
    private boolean hasInteracted = false;

    public MultiInteractA(final GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketTypes.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = packetFactory.clientInteractEntity(event);
            int entity = packet.getEntityId();
            boolean sneaking = packet.isSneaking().orElse(false);

            if (hasInteracted && entity != lastEntity) {
                String verbose = "lastEntity=" + lastEntity + ", entity=" + entity
                        + ", lastSneaking=" + lastSneaking + ", sneaking=" + sneaking;
                if (!player.canSkipTicks()) {
                    if (flagAndAlert(verbose) && shouldModifyPackets()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                    }
                } else {
                    flags.add(verbose);
                }
            }

            lastEntity = entity;
            lastSneaking = sneaking;
            hasInteracted = true;
        }

        if (player.gamemode == GameMode.SPECTATOR || isTickPacket(event.getPacketType())) {
            hasInteracted = false;
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
