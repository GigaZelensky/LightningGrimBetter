package ac.grim.grimac.platform.fabric.mc1161.util.convert;

import ac.grim.grimac.api.packet.item.PacketItemStack;
import ac.grim.grimac.api.util.LogUtil;
import ac.grim.grimac.platform.fabric.utils.convert.IFabricConversionUtil;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import io.github.retrooper.packetevents.adventure.serializer.gson.GsonComponentSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import net.kyori.adventure.text.Component;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

public class Fabric1140ConversionUtil implements IFabricConversionUtil {
    public PacketItemStack fromFabricItemStack(net.minecraft.item.ItemStack fabricStack) {
        if (fabricStack.isEmpty()) {
            return PacketItemStack.EMPTY;
        }

        ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer();
        try {
            PacketByteBuf packetByteBuf = new PacketByteBuf(buffer);
            packetByteBuf.writeItemStack(fabricStack);
            PacketWrapper<?> wrapper = PacketWrapper.createUniversalPacketWrapper(buffer);
            return wrapper.readItemStack();
        } catch (Exception e) {
            LogUtil.exception("Failed to encode ItemStack: {}" + fabricStack, e);
            return PacketItemStack.EMPTY;
        } finally {
            ByteBufHelper.release(buffer);
        }
    }

    public Text toNativeText(Component component) {
        return Text.Serializer.fromJson(GsonComponentSerializer.gson().serializeToTree(component));
    }
}
