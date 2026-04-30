package ac.grim.grimac.events.packets;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisconnect;
import org.jetbrains.annotations.NotNull;

public class PacketPluginMessage extends PacketListenerAbstract {
    public void onPacketReceive(@NotNull PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLUGIN_MESSAGE
                && event.getPacketType() != PacketType.Configuration.Client.PLUGIN_MESSAGE) return;

        WrapperPlayClientPluginMessage packet = new WrapperPlayClientPluginMessage(event);

        if (packet.getChannelName().equals("vv:proxy_details") && !GrimAPI.INSTANCE.getConfigManager().isIgnoreProxyViaVersion()) {
            User user = event.getUser();

            LogUtil.error(
                    user.getName() + " seems to have connected through a proxy running ViaVersion. "
                    + "Having ViaVersion installed on the proxy is incompatible with GrimAC and causes many issues. "
                    + "Please remove ViaVersion from your proxy server and install it on your backend servers instead. "
                    + "Alternatively, you can disable this message and allow players from proxy servers running ViaVersion "
                    + "by setting ignore-proxy-viaversion: true in config.yml, however you will not receive support."
            );

            WrapperPlayServerDisconnect disconnect = new WrapperPlayServerDisconnect(
                    MessageUtil.miniMessage(GrimAPI.INSTANCE.getConfigManager().getDisconnectPacketError())
            );

            user.sendPacket(disconnect);
            user.closeConnection();
        }
    }
}
