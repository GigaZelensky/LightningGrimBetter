package ac.grim.grimac.checks.impl.crash;

import ac.grim.grimac.api.packet.types.PacketTypes;
import ac.grim.grimac.api.packet.types.client.play.ClientClickWindowPacket;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import ac.grim.grimac.api.packet.types.event.PacketSendEvent;
import ac.grim.grimac.api.packet.types.server.play.ServerOpenWindowPacket;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.inventory.inventory.MenuType;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;

@CheckData(name = "CrashD", description = "Clicking slots in lectern window")
public class CrashD extends Check implements PacketCheck {

    private MenuType type = MenuType.UNKNOWN;
    private int lecternId = -1;

    public CrashD(GrimPlayer playerData) {
        super(playerData);
    }

    @Override
    public void onPacketSend(final PacketSendEvent event) {
        if (event.getPacketType() == PacketTypes.Play.Server.OPEN_WINDOW && isSupportedVersion()) {
            ServerOpenWindowPacket window = packetFactory.serverOpenWindow(event);
            this.type = MenuType.getMenuType(window.getType());
            if (type == MenuType.LECTERN) lecternId = window.getContainerId();
        }
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() == PacketTypes.Play.Client.CLICK_WINDOW && isSupportedVersion()) {
            ClientClickWindowPacket click = packetFactory.clientClickWindow(event);
            int clickType = click.getWindowClickType().ordinal();
            int button = click.getButton();
            int windowId = click.getWindowId();

            if (type == MenuType.LECTERN && windowId > 0 && windowId == lecternId) {
                if (flagAndAlert("clickType=" + clickType + " button=" + button)) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            }
        }
    }

    private boolean isSupportedVersion() {
        return PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_14);
    }

}
