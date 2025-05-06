package ac.grim.grimac.platform.bukkit.player;

import ac.grim.grimac.api.packet.item.PacketItemStack;
import ac.grim.grimac.api.platform.player.PlatformPlayerInventory;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.entity.Player;

public class BukkitPlatformInventory implements PlatformPlayerInventory {

    private final Player bukkitPlayer;

    public BukkitPlatformInventory(Player bukkitPlayer) {
        this.bukkitPlayer = bukkitPlayer;
    }

    @Override
    public PacketItemStack getItemInHand() {
        return SpigotConversionUtil.fromBukkitItemStack(bukkitPlayer.getInventory().getItemInHand());
    }

    @Override
    public PacketItemStack getItemInOffHand() {
        return SpigotConversionUtil.fromBukkitItemStack(bukkitPlayer.getInventory().getItemInOffHand());
    }

    @Override
    public PacketItemStack getStack(int bukkitSlot, int vanillaSlot) {
        return SpigotConversionUtil.fromBukkitItemStack(bukkitPlayer.getInventory().getItem(bukkitSlot));
    }

    @Override
    public PacketItemStack getHelmet() {
        return SpigotConversionUtil.fromBukkitItemStack(bukkitPlayer.getInventory().getHelmet());
    }

    @Override
    public PacketItemStack getChestplate() {
        return SpigotConversionUtil.fromBukkitItemStack(bukkitPlayer.getInventory().getChestplate());
    }

    @Override
    public PacketItemStack getLeggings() {
        return SpigotConversionUtil.fromBukkitItemStack(bukkitPlayer.getInventory().getLeggings());
    }

    @Override
    public PacketItemStack getBoots() {
        return SpigotConversionUtil.fromBukkitItemStack(bukkitPlayer.getInventory().getBoots());
    }

    @Override
    public PacketItemStack[] getContents() {
        org.bukkit.inventory.ItemStack[] bukkitItems = bukkitPlayer.getInventory().getContents();
        PacketItemStack[] items = new PacketItemStack[bukkitItems.length];
        for (int i = 0; i < bukkitItems.length; i++) {
            if (bukkitItems[i] == null) continue;
            items[i] = SpigotConversionUtil.fromBukkitItemStack(bukkitItems[i]);
        }
        return items;
    }

    @Override
    public String getOpenInventoryKey() {
        return bukkitPlayer.getOpenInventory().getType().toString();
    }
}
