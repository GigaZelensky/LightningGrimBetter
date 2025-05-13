package ac.grim.grimac.manager.init.load;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.packet.MCPacket;
import ac.grim.grimac.api.packet.item.PacketEnchantmentTypes;
import ac.grim.grimac.api.packet.item.PacketItemTypes;
import ac.grim.grimac.api.platform.init.LoadableInitable;
import ac.grim.grimac.api.util.LogUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.protocol.chat.ChatTypes;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import ac.grim.grimac.api.packet.entity.PacketEntityTypes;
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;

import java.util.concurrent.Executors;

public class PacketEventsInit implements LoadableInitable {

    PacketEventsAPI<?> packetEventsAPI;

    public PacketEventsInit(PacketEventsAPI<?> packetEventsAPI) {
        this.packetEventsAPI = packetEventsAPI;
    }

    @Override
    public void load() {
        LogUtil.info("Loading PacketEvents...");
        MCPacket.setAPI(GrimAPI.INSTANCE.getLoader().getMCPacketAPI());
        PacketEvents.setAPI(packetEventsAPI);
        PacketEvents.getAPI().getSettings()
                .fullStackTrace(true)
                .kickOnPacketException(true)
                .checkForUpdates(false)
                .reEncodeByDefault(false)
                .debug(false);
        PacketEvents.getAPI().load();
        // This may seem useless, but it causes java to start loading stuff async before we need it
        Executors.defaultThreadFactory().newThread(() -> {
            StateTypes.AIR.getName();
            PacketItemTypes.AIR.getName();
            PacketEntityTypes.PLAYER.getName();
            EntityDataTypes.BOOLEAN.getName();
            ChatTypes.CHAT.getName();
            PacketEnchantmentTypes.ALL_DAMAGE_PROTECTION.getName();
            ParticleTypes.DUST.getName();
        }).start();
    }
}
