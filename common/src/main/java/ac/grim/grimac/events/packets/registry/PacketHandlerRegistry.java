package ac.grim.grimac.events.packets.registry;

import ac.grim.grimac.api.packet.types.PacketType;
import ac.grim.grimac.api.packet.types.PacketTypes;
import ac.grim.grimac.api.packet.types.RecievablePacket;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
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
    private final Map<PacketTypeCommon, List<Consumer<T>>> handlers = new IdentityHashMap<>();
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
            for (PacketTypes.Play.Client packet : PacketTypes.Play.Client.values()) {
                if (typePredicate.test(packet)) types.add(packet);
            }
            for (PacketTypes.Login.Client packet : PacketTypes.Login.Client.values()) {
                if (typePredicate.test(packet)) types.add(packet);
            }
            for (PacketTypes.Status.Client packet : PacketTypes.Status.Client.values()) {
                if (typePredicate.test(packet)) types.add(packet);
            }
            for (PacketTypes.Configuration.Client packet : PacketTypes.Configuration.Client.values()) {
                if (typePredicate.test(packet)) types.add(packet);
            }
            for (PacketTypes.Handshaking.Client packet : PacketTypes.Handshaking.Client.values()) {
                if (typePredicate.test(packet)) types.add(packet);
            }
        } else {
            for (PacketTypes.Play.Server packet : PacketTypes.Play.Server.values()) {
                if (typePredicate.test(packet)) types.add(packet);
            }
            for (PacketTypes.Login.Server packet : PacketTypes.Login.Server.values()) {
                if (typePredicate.test(packet)) types.add(packet);
            }
            for (PacketTypes.Status.Server packet : PacketTypes.Status.Server.values()) {
                if (typePredicate.test(packet)) types.add(packet);
            }
            for (PacketTypes.Configuration.Server packet : PacketTypes.Configuration.Server.values()) {
                if (typePredicate.test(packet)) types.add(packet);
            }
            for (PacketTypes.Handshaking.Server packet : PacketTypes.Handshaking.Server.values()) {
                if (typePredicate.test(packet)) types.add(packet);
            }
        }
        registerHandler(consumer, types.toArray(PacketTypeCommon[]::new));
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