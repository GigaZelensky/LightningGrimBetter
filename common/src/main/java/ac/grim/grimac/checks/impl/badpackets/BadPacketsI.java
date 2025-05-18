package ac.grim.grimac.checks.impl.badpackets;

import ac.grim.grimac.api.packet.types.PacketTypes;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerAbilities;

@CheckData(name = "BadPacketsI", description = "Claimed to be flying while unable to fly")
public class BadPacketsI extends Check implements PacketCheck {
    public BadPacketsI(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketTypes.Play.Client.PLAYER_ABILITIES) {
            if (new WrapperPlayClientPlayerAbilities(event).isFlying() && !player.canFly) {
                if (flagAndAlert() && shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            }
        }
    }
}
