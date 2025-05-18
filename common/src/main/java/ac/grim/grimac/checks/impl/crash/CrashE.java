package ac.grim.grimac.checks.impl.crash;

import ac.grim.grimac.api.packet.types.PacketTypes;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;

@CheckData(name = "CrashE")
public class CrashE extends Check implements PacketCheck {

    public CrashE(GrimPlayer playerData) {
        super(playerData);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() == PacketTypes.Play.Client.CLIENT_SETTINGS) {
            WrapperPlayClientSettings wrapper = new WrapperPlayClientSettings(event);
            int viewDistance = wrapper.getViewDistance();
            if (viewDistance < 2) {
                flagAndAlert("distance=" + viewDistance);
                wrapper.setViewDistance(2);
            }
        }
    }

}
