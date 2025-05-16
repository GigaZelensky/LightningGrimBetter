package ac.grim.grimac.manager;

import ac.grim.grimac.api.packet.types.client.play.ClientInteractEntityPacket;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.events.packets.registry.PacketHandlerRegistry;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
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
    public void onPacketReceive(final PacketHandlerRegistry<PacketReceiveEvent> registry) {
        registry.registerWrapperHandler((action, event) -> {
            if (action.action() == ClientInteractEntityPacket.InteractAction.ATTACK) {
                player.totalFlyingPacketsSent = 0;
                attacking = true;
                lastAttack = System.currentTimeMillis();
            }
        }, ClientInteractEntityPacket.class);

        // TODO (Packet Rewrite) (Registry) (Optimize) optimize more later
        registry.registerHandler(event -> {
            if (event.getPacketType() == PacketTypes.Play.Client.INTERACT_ENTITY) return;
            if (isTickPacketIncludingNonMovement(event.getPacketType())) {
                player.totalFlyingPacketsSent++;
                attacking = false;
            }
        });
    }
}
