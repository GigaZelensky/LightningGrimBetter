package ac.grim.grimac.platform.fabric.utils.convert;

import ac.grim.grimac.api.packet.item.PacketItemStack;
import net.kyori.adventure.text.Component;
import net.minecraft.text.Text;

public interface IFabricConversionUtil {
    PacketItemStack fromFabricItemStack(net.minecraft.item.ItemStack fabricStack);
    Text toNativeText(Component component);
}
