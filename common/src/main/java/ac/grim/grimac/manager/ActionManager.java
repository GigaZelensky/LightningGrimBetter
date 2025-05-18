package ac.grim.grimac.manager;

import ac.grim.grimac.api.packet.types.client.play.ClientInteractEntityPacket;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import ac.grim.grimac.api.packet.types.PacketTypes;
import lombok.Getter;

@Getter
public class ActionManager extends Check implements PacketCheck {
    private boolean attacking = false;
    private long lastAttack = 0;

    public ActionManager(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() == PacketTypes.Play.Client.INTERACT_ENTITY) {
            ClientInteractEntityPacket action = packetFactory.clientInteractEntity(event);
            if (action.getInteractAction() == ClientInteractEntityPacket.InteractAction.ATTACK) {
                player.totalFlyingPacketsSent = 0;
                attacking = true;
                lastAttack = System.currentTimeMillis();
            }
        } else if (isTickPacketIncludingNonMovement(event.getPacketType())) {
            player.totalFlyingPacketsSent++;
            attacking = false;
        }
    }

    public boolean hasAttackedSince(long time) {
        return System.currentTimeMillis() - lastAttack < time;
    }
}
