package ac.grim.grimac.events.packets.registry;

import ac.grim.grimac.api.packet.types.PacketType;
import ac.grim.grimac.api.packet.types.PacketTypes;
import ac.grim.grimac.api.packet.types.RecievablePacket;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import lombok.Getter;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class PacketHandlerRegistry<T extends PacketReceiveEvent> {
    @Getter
    private final Map<PacketType, List<Consumer<T>>> handlers = new IdentityHashMap<>();
    private final boolean serverbound;

    public PacketHandlerRegistry(boolean serverbound) {
        this.serverbound = serverbound;
    }

    public void registerHandler(Consumer<T> consumer, ac.grim.grimac.api.packet.types.PacketType... types) {
        if (types.length == 0) {
            registerHandler(consumer, type -> true);
            return;
        }
        for (ac.grim.grimac.api.packet.types.PacketType type : types) {
            handlers.computeIfAbsent(type, __ -> new ArrayList<>()).add(consumer);
        }
    }

    public void registerHandler(Consumer<T> consumer, Predicate<ac.grim.grimac.api.packet.types.PacketType> typePredicate) {
        List<ac.grim.grimac.api.packet.types.PacketType> types = new ArrayList<>();
        if (serverbound) {
            for (PacketType packet: PacketTypes.getC2S()) {
                if (typePredicate.test(packet)) types.add(packet);
            }
        } else {
            for (PacketType packet : PacketTypes.getS2C()) {
                if (typePredicate.test(packet)) types.add(packet);
            }
        }
        registerHandler(consumer, types.toArray(PacketType[]::new));
    }

    public void registerHandler(Runnable runnable, PacketType type) {
    }

    public <W extends RecievablePacket> void registerWrapperHandler(
            BiConsumer<W, T> consumer,
            Class<W> wrapperClass
    ) {

    }

    public <W extends RecievablePacket> void registerWrapperHandler(
            Consumer<W> consumer,
            Class<W> wrapperClass
    ) {

    }
}
