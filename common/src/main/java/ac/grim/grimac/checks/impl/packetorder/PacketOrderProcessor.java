package ac.grim.grimac.checks.impl.packetorder;

import ac.grim.grimac.api.packet.types.PacketTypes;
import ac.grim.grimac.api.packet.types.client.play.*;
import ac.grim.grimac.api.packet.world.enums.BlockFace;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.events.packets.registry.PacketHandlerRegistry;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import lombok.Getter;
import org.jetbrains.annotations.Contract;

@Getter
public final class PacketOrderProcessor extends Check implements PacketCheck {
    public PacketOrderProcessor(final GrimPlayer player) {
        super(player);
    }

    private boolean openingInventory; // only pre 1.12 clients on pre 1.12 servers
    private boolean swapping;
    private boolean dropping;
    private boolean interacting;
    private boolean attacking;
    private boolean releasing;
    private boolean digging;
    private boolean sprinting;
    private boolean sneaking;
    private boolean placing;
    private boolean using;
    private boolean picking;
    private boolean clickingInInventory;
    private boolean closingInventory;
    private boolean quickMoveClicking;
    private boolean pickUpClicking;
    private boolean leavingBed;
    private boolean startingToGlide;
    private boolean jumpingWithMount;

    @Override
    public void onPacketReceive(PacketHandlerRegistry<PacketReceiveEvent> registry) {
        registry.registerWrapperHandler((packet) -> {
            if (packet.getAction() == ClientStatusPacket.Action.OPEN_INVENTORY_ACHIEVEMENT) {
                openingInventory = true;
            }
        }, ClientStatusPacket.class);

        registry.registerWrapperHandler((packet) -> {
            if (packet.action() == ClientInteractEntityPacket.InteractAction.ATTACK) {
                attacking = true;
            } else {
                interacting = true;
            }
        }, ClientInteractEntityPacket.class);

        registry.registerWrapperHandler((packet) -> {
            switch (packet.action()) {
                case SWAP_ITEM_WITH_OFFHAND -> swapping = true;
                case DROP_ITEM, DROP_ITEM_STACK -> dropping = true;
                case RELEASE_USE_ITEM -> releasing = true;
                case FINISHED_DIGGING, CANCELLED_DIGGING, START_DIGGING -> digging = true;
            }
        }, ClientPlayerDiggingPacket.class);

        registry.registerWrapperHandler((packet) -> {
            switch (packet.action()) {
                case START_SPRINTING, STOP_SPRINTING -> {
                    if (!player.inVehicle()) {
                        sprinting = true;
                    }
                }
                case STOP_SNEAKING, START_SNEAKING -> sneaking = true;
                case LEAVE_BED -> leavingBed = true;
                case START_FLYING_WITH_ELYTRA -> startingToGlide = true;
                case OPEN_HORSE_INVENTORY -> openingInventory = true;
                case START_JUMPING_WITH_HORSE, STOP_JUMPING_WITH_HORSE -> jumpingWithMount = true;
            }
        }, ClientEntityActionPacket.class);

        registry.registerHandler(() -> using = true, PacketTypes.Play.Client.USE_ITEM);

        registry.registerWrapperHandler((packet) -> {
            if (packet.blockFace() == BlockFace.OTHER) {
                using = true;
            } else {
                placing = true;
            }
        }, ClientPlayerBlockPlacementPacket.class);

        registry.registerHandler(() -> picking = true, PacketTypes.Play.Client.PICK_ITEM);

        registry.registerWrapperHandler((packet) -> {
            switch (packet.windowClickType()) {
                case QUICK_MOVE -> quickMoveClicking = true;
                case PICKUP, PICKUP_ALL -> pickUpClicking = true;
            }
        }, ClientClickWindow.class);

        registry.registerHandler(() -> closingInventory = true, PacketTypes.Play.Client.CLOSE_WINDOW);

        // TODO (Packet Rewrite) (Registry) (Optimization) confirm this is correct
        registry.registerHandler(() -> {
            if (player.gamemode == GameMode.SPECTATOR || isTickPacket(packetType)) {
                openingInventory = false;
                swapping = false;
                dropping = false;
                attacking = false;
                interacting = false;
                releasing = false;
                digging = false;
                placing = false;
                using = false;
                picking = false;
                sprinting = false;
                sneaking = false;
                clickingInInventory = false;
                closingInventory = false;
                quickMoveClicking = false;
                pickUpClicking = false;
                leavingBed = false;
                startingToGlide = false;
                jumpingWithMount = false;
            }
        }, POSSIBLE_TICK_PACKET_TYPES);
    }

    @Contract(pure = true)
    public boolean isRightClicking() {
        return placing || using || interacting;
    }
}
