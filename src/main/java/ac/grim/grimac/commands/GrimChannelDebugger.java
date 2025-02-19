package ac.grim.grimac.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CommandAlias("grim|grimac")
public class GrimChannelDebugger extends BaseCommand implements Listener {

    // Stores players' registered plugin message channels
    private final ConcurrentHashMap<UUID, Set<String>> playerChannels = new ConcurrentHashMap<>();

    public GrimChannelDebugger(JavaPlugin plugin) {
        // Register event listener for cleanup
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Register packet listener
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (!(event.getPlayer() instanceof Player player)) return;

                if (isPluginMessagePacket(event.getPacketType())) {
                    WrapperPlayClientPluginMessage packet = new WrapperPlayClientPluginMessage(event);
                    UUID playerId = player.getUniqueId();
                    Set<String> channels = playerChannels.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());

                    String channelName = packet.getChannelName();
                    String payload = new String(packet.getData(), StandardCharsets.UTF_8);

                    if (channelName.equals("minecraft:register") || channelName.equals("minecraft:unregister")) {
                        String[] fabricChannels = payload.split("\0");

                        if (channelName.equals("minecraft:register")) {
                            Collections.addAll(channels, fabricChannels);
                        } else {
                            for (String channel : fabricChannels) {
                                channels.remove(channel);
                            }
                        }
                    } else {
                        channels.add(channelName);
                    }
                }
            }
        });
    }

    private boolean isPluginMessagePacket(PacketType<?> packetType) {
        return packetType == PacketType.Play.Client.PLUGIN_MESSAGE ||
               packetType == PacketType.Configuration.Client.PLUGIN_MESSAGE;
    }

    @Subcommand("debugchannel")
    @CommandPermission("grim.debugchannel")
    @CommandCompletion("@players")
    public void onDebugChannel(CommandSender sender, @Optional OnlinePlayer target) {
        Player targetPlayer = (target != null) ? target.getPlayer() :
                             ((sender instanceof Player) ? (Player) sender : null);

        if (targetPlayer == null) {
            sender.sendMessage("§cUsage: /grim debugchannel <player>");
            return;
        }

        Set<String> channels = playerChannels.getOrDefault(targetPlayer.getUniqueId(), Collections.emptySet());
        sender.sendMessage("§aRegistered Channels for " + targetPlayer.getName() + ":");

        if (channels.isEmpty()) {
            sender.sendMessage("§7No channels recorded for this player in this session.");
        } else {
            channels.forEach(channel -> sender.sendMessage("§7- §f" + channel));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerChannels.remove(event.getPlayer().getUniqueId());
    }
}
