package ac.grim.grimac.utils.latency;

import ac.grim.grimac.api.packet.ResourceLocationI;
import ac.grim.grimac.api.packet.component.PacketComponentTypes;
import ac.grim.grimac.api.packet.component.PacketComponentItemUseCooldown;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.type.PositionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PositionUpdate;
import ac.grim.grimac.utils.data.CooldownData;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import ac.grim.grimac.api.packet.item.PacketItemStack;
import com.github.retrooper.packetevents.resources.ResourceLocation;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Applies a cooldown period to all items with the given type. Used by the Notchian server with enderpearls.
// This packet should be sent when the cooldown starts and also when the cooldown ends (to compensate for lag),
// although the client will end the cooldown automatically. Can be applied to any item,
// note that interactions still get sent to the server with the item but the client does not play the animation
// nor attempt to predict results (i.e block placing).
public class CompensatedCooldown extends Check implements PositionCheck {

    private final ConcurrentHashMap<ResourceLocationI, CooldownData> itemCooldownMap = new ConcurrentHashMap<>();

    public CompensatedCooldown(GrimPlayer playerData) {
        super(playerData);
    }

    @Override
    public void onPositionUpdate(final PositionUpdate positionUpdate) {
        for (Iterator<Map.Entry<ResourceLocationI, CooldownData>> it = itemCooldownMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ResourceLocationI, CooldownData> entry = it.next();

            // Only tick if we have known that this packet has arrived
            if (entry.getValue().getTransaction() < player.lastTransactionReceived.get()) {
                entry.getValue().tick();
            }

            // The client will automatically remove cooldowns after enough time
            if (entry.getValue().getTicksRemaining() <= 0) it.remove();
        }
    }

    // all the same to us... having a cooldown or not having one
    public boolean hasItem(PacketItemStack item) {
        // 1.21.2+ uses this stupid logic of cooldown groups
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_21_2)) {
            PacketComponentItemUseCooldown cooldown = item.getComponentOr(PacketComponentTypes.USE_COOLDOWN, null);
            if (cooldown != null) {
                final Optional<ResourceLocationI> cooldownGroup = cooldown.getCooldownGroup();
                // If the cooldown group is present, it uses that.
                // Otherwise, it uses the id of the item.
                if (cooldownGroup.isPresent()) {
                    return itemCooldownMap.containsKey(cooldownGroup.get());
                }
            }
        }

        return itemCooldownMap.containsKey(item.getType().getName());
    }

    // Yes, new cooldowns overwrite old ones, we don't have to check for an existing cooldown
    public void addCooldown(ResourceLocation location, int cooldown, int transaction) {
        if (cooldown == 0) {
            removeCooldown(location);
            return;
        }

        itemCooldownMap.put(location, new CooldownData(cooldown, transaction));
    }

    public void removeCooldown(ResourceLocation location) {
        itemCooldownMap.remove(location);
    }
}
