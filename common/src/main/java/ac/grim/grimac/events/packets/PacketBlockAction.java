package ac.grim.grimac.events.packets;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.packet.types.PacketTypes;
import ac.grim.grimac.api.packet.types.event.ListenerPriority;
import ac.grim.grimac.api.packet.types.event.PacketListenerInterface;
import ac.grim.grimac.api.packet.util.vec.ImmutableVector3i;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.ShulkerData;
import ac.grim.grimac.utils.nmsutil.Materials;
import ac.grim.grimac.api.packet.types.event.PacketSendEvent;
import ac.grim.grimac.api.packet.block.PacketBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction;

// If a player doesn't get this packet, then they don't know the shulker box is currently opened
// Meaning if a player enters a chunk with an opened shulker box, they see the shulker box as closed.
//
// Exempting the player on shulker boxes is an option... but then you have people creating PvP arenas
// on shulker boxes to get high lenience.
//
public class PacketBlockAction implements PacketListenerInterface {

    @Override
    public int getListenerPriority() {
        return ListenerPriority.HIGH;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketTypes.Play.Server.BLOCK_ACTION) {
            GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (player == null) return;

            WrapperPlayServerBlockAction blockAction = new WrapperPlayServerBlockAction(event);
            ImmutableVector3i blockPos = blockAction.getBlockPosition();

            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                // The client ignores the state sent to the client.
                PacketBlockState existing = player.compensatedWorld.getBlock(blockPos);
                if (Materials.isShulker(existing.getType())) {
                    // Param is the number of viewers of the shulker box.
                    // Hashset with .equals() set to be position
                    if (blockAction.getActionData() >= 1) {
                        ShulkerData data = new ShulkerData(blockPos, player.lastTransactionSent.get(), false);
                        player.compensatedWorld.openShulkerBoxes.remove(data);
                        player.compensatedWorld.openShulkerBoxes.add(data);
                    } else {
                        // The shulker box is closing
                        ShulkerData data = new ShulkerData(blockPos, player.lastTransactionSent.get(), true);
                        player.compensatedWorld.openShulkerBoxes.remove(data);
                        player.compensatedWorld.openShulkerBoxes.add(data);
                    }
                }
            });
        }
    }
}
