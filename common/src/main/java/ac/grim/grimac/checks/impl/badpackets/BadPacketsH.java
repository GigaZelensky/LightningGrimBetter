package ac.grim.grimac.checks.impl.badpackets;

import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.BlockPlaceCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.BlockBreak;
import ac.grim.grimac.utils.anticheat.update.BlockPlace;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import ac.grim.grimac.api.packet.types.PacketTypes;

@CheckData(name = "BadPacketsH", description = "Sent unexpected sequence id", experimental = true)
public class BadPacketsH extends BlockPlaceCheck {
    private int lastSequence;
    private final boolean isSupportedVersion = player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_19) && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_19);

    public BadPacketsH(final GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketTypes.Play.Client.USE_ITEM
                && shouldCancel(packetFactory.clientPlayerUseItem(event).getSequence())) {
            event.setCancelled(true);
            player.onPacketCancel();
        }
    }

    @Override
    public void onBlockPlace(BlockPlace place) {
        if (shouldCancel(place.sequence) && shouldCancel()) {
            place.resync();
        }
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        switch (blockBreak.action) {
            case START_DIGGING, FINISHED_DIGGING -> {
                if (shouldCancel(blockBreak.sequence)) {
                    blockBreak.cancel();
                }
            }
            case CANCELLED_DIGGING -> { // other actions will be checked by BadPacketsL
                if (blockBreak.sequence != 0 && flagAndAlert("expected=0, id=" + blockBreak.sequence) && shouldModifyPackets()) {
                    blockBreak.cancel();
                }
            }
        }
    }

    public boolean shouldCancel(int sequence) {
        if (isSupportedVersion && sequence != lastSequence + 1) {
            if (flagAndAlert("expected=" + (lastSequence + 1) + ", id=" + sequence) && shouldModifyPackets()) {
                lastSequence = sequence;
                return true;
            }
        }

        lastSequence = sequence;
        return false;
    }

    public void onWorldChange() {
        lastSequence = 0;
    }
}
