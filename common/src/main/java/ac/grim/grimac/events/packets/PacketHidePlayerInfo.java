package ac.grim.grimac.events.packets;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.packet.types.event.PacketSendEvent;
import ac.grim.grimac.api.packet.types.server.play.ServerPlayerInfoPacket;
import ac.grim.grimac.api.packet.types.server.play.ServerPlayerInfoUpdatePacket;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import ac.grim.grimac.api.packet.types.PacketTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class PacketHidePlayerInfo extends PacketListenerAbstract {

    public PacketHidePlayerInfo() {
        super(PacketListenerPriority.HIGHEST);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketTypes.Play.Server.PLAYER_INFO) {
            //iterate through players and fake their game mode if they are spectating via grim spectate
            if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThanOrEquals(ServerVersion.V_1_12_2))
                return;

            GrimPlayer receiver = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());

            if (receiver == null) { // Exempt
                return;
            }

            ServerPlayerInfoPacket info = new ServerPlayerInfoPacket(event);

            if (info.getAction() == ServerPlayerInfoPacket.Action.UPDATE_GAME_MODE || info.getAction() == ServerPlayerInfoPacket.Action.ADD_PLAYER) {
                List<ServerPlayerInfoPacket.PlayerData> nmsPlayerInfoDataList = info.getPlayerDataList();

                int hideCount = 0;
                for (ServerPlayerInfoPacket.PlayerData playerData : nmsPlayerInfoDataList) {
                    if (GrimAPI.INSTANCE.getSpectateManager().shouldHidePlayer(receiver, playerData)) {
                        hideCount++;
                        if (playerData.getGameMode() == GameMode.SPECTATOR)
                            playerData.setGameMode(GameMode.SURVIVAL);
                    }
                }

                //if amount of hidden players is the amount of players updated & is an update game mode action just cancel it
                if (hideCount == nmsPlayerInfoDataList.size() && info.getAction() == ServerPlayerInfoPacket.Action.UPDATE_GAME_MODE) {
                    event.setCancelled(true);
                } else if (hideCount > 0) {
                    event.markForReEncode(true);
                }
            }
        } else if (event.getPacketType() == PacketTypes.Play.Server.PLAYER_INFO_UPDATE) {
            GrimPlayer receiver = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getUser());
            if (receiver == null) return;
            //create wrappers
            ServerPlayerInfoUpdatePacket wrapper = new ServerPlayerInfoUpdatePacket(event);
            EnumSet<ServerPlayerInfoUpdatePacket.Action> actions = wrapper.getActions();
            //player's game mode updated
            if (actions.contains(ServerPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE)) {
                boolean onlyGameMode = actions.size() == 1; // packet is being sent to only update game modes
                int hideCount = 0;
                List<ServerPlayerInfoUpdatePacket.PlayerInfo> modified = new ArrayList<>(wrapper.getEntries().size());
                //iterate through the player entries
                for (ServerPlayerInfoUpdatePacket.PlayerInfo entry : wrapper.getEntries()) {
                    //check if the player should be hidden
                    ServerPlayerInfoUpdatePacket.PlayerInfo modifiedPacket = null;
                    final UserProfile gameProfile = entry.getGameProfile();
                    if (GrimAPI.INSTANCE.getSpectateManager().shouldHidePlayer(receiver, gameProfile.getUUID())) {
                        hideCount++;
                        //modify & create a new packet from pre-existing one if they are a spectator
                        if (entry.getGameMode() == GameMode.SPECTATOR) {
                            modifiedPacket = new ServerPlayerInfoUpdate.PlayerInfo(
                                    gameProfile,
                                    entry.isListed(),
                                    entry.getLatency(),
                                    GameMode.SURVIVAL,
                                    entry.getDisplayName(),
                                    entry.getChatSession()
                            );
                            modified.add(modifiedPacket);
                        }
                    }

                    if (modifiedPacket == null) {  //if the packet wasn't modified, send original
                        modified.add(entry);
                    } else if (!onlyGameMode) { //if more than just the game mode updated, modify the packet
                        modified.add(modifiedPacket);
                    } //if only the game mode was updated and the packet was modified, don't send anything

                }

                // if the amount of hidden players & modified entries are the same
                if (hideCount == modified.size()) {
                    if (onlyGameMode) { // if only the game mode changed, cancel
                        event.setCancelled(true);
                    } else { //if more than the game mode changed, remove the action
                        wrapper.getActions().remove(ServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE);
                        event.markForReEncode(true);
                    }
                } else { //modify entries
                    wrapper.setEntries(modified);
                    event.markForReEncode(true);
                }
            }
        }
    }
}
