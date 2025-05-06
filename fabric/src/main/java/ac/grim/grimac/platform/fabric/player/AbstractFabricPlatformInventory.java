package ac.grim.grimac.platform.fabric.player;

import ac.grim.grimac.api.platform.player.PlatformPlayerInventory;
import ac.grim.grimac.platform.fabric.GrimACFabricLoaderPlugin;
import ac.grim.grimac.platform.fabric.utils.convert.IFabricConversionUtil;
import ac.grim.grimac.api.packet.item.PacketItemStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.server.network.ServerPlayerEntity;


public abstract class AbstractFabricPlatformInventory implements PlatformPlayerInventory {

    private static final IFabricConversionUtil fabricConversionUtil = GrimACFabricLoaderPlugin.LOADER.getFabricConversionUtil();
    protected ServerPlayerEntity fabricPlayer;
    protected PlayerInventory inventory;

    public AbstractFabricPlatformInventory(ServerPlayerEntity player) {
        this.fabricPlayer = player;
        this.inventory = player.inventory;
    }

    @Override
    public PacketItemStack getItemInHand() {
        return fabricConversionUtil.fromFabricItemStack(inventory.getMainHandStack());
    }

    @Override
    public PacketItemStack getItemInOffHand() {
        return fabricConversionUtil.fromFabricItemStack(inventory.getStack(40));
    }

    @Override
    public PacketItemStack getStack(int bukkitSlot, int vanillaSlot) {
        return fabricConversionUtil.fromFabricItemStack(inventory.getStack(bukkitSlot));
    }

    @Override
    public PacketItemStack getHelmet() {
        return fabricConversionUtil.fromFabricItemStack(inventory.getStack(39));
    }

    @Override
    public PacketItemStack getChestplate() {
        return fabricConversionUtil.fromFabricItemStack(inventory.getStack(38));
    }

    @Override
    public PacketItemStack getLeggings() {
        return fabricConversionUtil.fromFabricItemStack(inventory.getStack(37));
    }

    @Override
    public PacketItemStack getBoots() {
        return fabricConversionUtil.fromFabricItemStack(inventory.getStack(36));
    }

    @Override
    public PacketItemStack[] getContents() {
        PacketItemStack[] items = new PacketItemStack[inventory.size()];
        for (int i = 0; i < inventory.size(); i++) {
            items[i] = fabricConversionUtil.fromFabricItemStack(inventory.getStack(i));
        }
        return items;
    }
}
