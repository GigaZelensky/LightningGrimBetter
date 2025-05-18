package ac.grim.grimac.checks.impl.badpackets;

import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.types.PacketTypes;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;

@CheckData(name = "BadPacketsE")
public class BadPacketsE extends Check implements PacketCheck {
    private int noReminderTicks;

    public BadPacketsE(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        final boolean isViaPleaseStopUsingProtocolHacksOnYourServer = player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_21_2) || PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_21_2);
        if (event.getPacketType() == PacketTypes.Play.Client.PLAYER_POSITION_AND_ROTATION ||
                event.getPacketType() == PacketTypes.Play.Client.PLAYER_POSITION) {
            noReminderTicks = 0;
        } else if (isFlying(event.getPacketType()) && !player.packetStateData.lastPacketWasTeleport) {
            noReminderTicks++;
        } else if (event.getPacketType() == PacketTypes.Play.Client.STEER_VEHICLE
                || (isViaPleaseStopUsingProtocolHacksOnYourServer && player.inVehicle())) {
            noReminderTicks = 0; // Exempt vehicles
        }

        if (noReminderTicks > 20) {
            flagAndAlert("ticks=" + noReminderTicks); // ban?  I don't know how this would false
        }
    }

    public void handleRespawn() {
        noReminderTicks = 0;
    }
}
