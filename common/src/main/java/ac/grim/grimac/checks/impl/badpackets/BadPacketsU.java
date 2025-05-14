package ac.grim.grimac.checks.impl.badpackets;

import ac.grim.grimac.api.packet.MCPacket;
import ac.grim.grimac.api.packet.item.PacketItemStack;
import ac.grim.grimac.api.packet.item.PacketItemTypes;
import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.types.client.play.ClientPlayerBlockPlacementPacket;
import ac.grim.grimac.api.packet.util.vec.ImmutableVector3f;
import ac.grim.grimac.api.packet.util.vec.ImmutableVector3i;
import ac.grim.grimac.api.packet.util.vec.Vector3f;
import ac.grim.grimac.api.packet.util.vec.Vector3i;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import ac.grim.grimac.api.packet.world.enums.BlockFace;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;

@CheckData(name = "BadPacketsU", description = "Sent impossible use item packet")
public class BadPacketsU extends Check implements PacketCheck {
    public BadPacketsU(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            final ClientPlayerBlockPlacementPacket packet = MCPacket.getAPI().packetFactory().newClientPlayerBlockPlacementPacket(event);
            // BlockFace.OTHER is USE_ITEM for pre 1.9
            if (packet.getFace() == BlockFace.OTHER) {

                // This packet is always sent at (-1, -1, -1) at (0, 0, 0) on the block
                // except y gets wrapped?
                final int expectedY = player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_8) ? 4095 : 255;

                final boolean failedItemCheck = packet.getItemStack().isPresent() && isEmpty(packet.getItemStack().get())
                        // ViaVersion can sometimes cause this part of the check to false
                        && player.getClientVersion().isOlderThan(PacketClientVersions.V_1_9);

                final ImmutableVector3i pos = packet.getBlockPosition();
                final ImmutableVector3f cursor = packet.getCursorPosition();

                if (failedItemCheck
                        || pos.getX() != -1
                        || pos.getY() != expectedY
                        || pos.getZ() != -1
                        || cursor.getX() != 0
                        || cursor.getY() != 0
                        || cursor.getZ() != 0
                        || packet.getSequence() != 0
                ) {
                    final String verbose = String.format(
                            "xyz=%s, %s, %s, cursor=%s, %s, %s, item=%s, sequence=%s",
                            pos.getX(), pos.getY(), pos.getZ(), cursor.getX(), cursor.getY(), cursor.getZ(), !failedItemCheck, packet.getSequence()
                    );
                    if (flagAndAlert(verbose) && shouldModifyPackets()) {
                        player.onPacketCancel();
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    private boolean isEmpty(PacketItemStack itemStack) {
        return itemStack.getType() == null || itemStack.getType() == PacketItemTypes.AIR;
    }
}
