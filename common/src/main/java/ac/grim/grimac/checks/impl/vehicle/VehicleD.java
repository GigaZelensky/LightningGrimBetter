package ac.grim.grimac.checks.impl.vehicle;

import ac.grim.grimac.api.packet.entity.PacketEntityType;
import ac.grim.grimac.api.packet.entity.PacketEntityTypes;
import ac.grim.grimac.api.packet.types.PacketTypes;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.api.packet.types.client.play.ClientEntityActionPacket;

@CheckData(name = "VehicleD", experimental = true, description = "Jumped in a vehicle that cannot jump")
public class VehicleD extends Check implements PacketCheck {
    public VehicleD(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() == PacketTypes.Play.Client.ENTITY_ACTION && packetFactory.clientEntityAction(event).getAction() == ClientEntityActionPacket.Action.START_JUMPING_WITH_HORSE) {
            final PacketEntityType vehicle = player.inVehicle() ? player.compensatedEntities.self.getRiding().getType() : null;

            if (!PacketEntityTypes.isTypeInstanceOf(vehicle, PacketEntityTypes.ABSTRACT_HORSE)) {
                flagAndAlert("vehicle=" + (vehicle == null ? "null" : vehicle.getName().getKey().toLowerCase()));
            }
        }
    }
}
