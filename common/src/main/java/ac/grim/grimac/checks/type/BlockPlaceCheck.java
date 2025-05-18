package ac.grim.grimac.checks.type;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.packet.item.PacketStateType;
import ac.grim.grimac.api.packet.util.vec.ImmutableVector3i;
import ac.grim.grimac.api.packet.world.PacketStateTypes;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.BlockPlace;
import ac.grim.grimac.utils.collisions.HitboxData;
import ac.grim.grimac.utils.collisions.datatypes.CollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.ComplexCollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags;

import java.util.ArrayList;
import java.util.List;

public class BlockPlaceCheck extends Check implements RotationCheck, BlockBreakCheck {
    private static final List<PacketStateType> weirdBoxes = new ArrayList<>();
    private static final List<PacketStateType> buggyBoxes = new ArrayList<>();

    static {
        // Fences and walls aren't worth checking.
        weirdBoxes.addAll(new ArrayList<>(BlockTags.FENCES.getStates()));
        weirdBoxes.addAll(new ArrayList<>(BlockTags.WALLS.getStates()));
        weirdBoxes.add(PacketStateTypes.LECTERN);

        buggyBoxes.addAll(new ArrayList<>(BlockTags.DOORS.getStates()));
        buggyBoxes.addAll(new ArrayList<>(BlockTags.STAIRS.getStates()));
        buggyBoxes.add(PacketStateTypes.CHEST);
        buggyBoxes.add(PacketStateTypes.TRAPPED_CHEST);
        buggyBoxes.add(PacketStateTypes.CHORUS_PLANT);

        // The client changes these block states around when placing blocks, temporary desync
        buggyBoxes.add(PacketStateTypes.KELP);
        buggyBoxes.add(PacketStateTypes.KELP_PLANT);
        buggyBoxes.add(PacketStateTypes.TWISTING_VINES);
        buggyBoxes.add(PacketStateTypes.TWISTING_VINES_PLANT);
        buggyBoxes.add(PacketStateTypes.WEEPING_VINES);
        buggyBoxes.add(PacketStateTypes.WEEPING_VINES_PLANT);
        buggyBoxes.add(PacketStateTypes.REDSTONE_WIRE);
    }

    private final SimpleCollisionBox[] boxes = new SimpleCollisionBox[ComplexCollisionBox.DEFAULT_MAX_COLLISION_BOX_SIZE];
    protected int cancelVL;

    public BlockPlaceCheck(GrimPlayer player) {
        super(player);
    }

    // Method called immediately after a block is placed, before forwarding block place to server
    public void onBlockPlace(final BlockPlace place) {
    }

    // Method called the flying packet after the block place
    public void onPostFlyingBlockPlace(BlockPlace place) {
    }

    @Override
    public void onReload(ConfigManager config) {
        this.cancelVL = config.getIntElse(getConfigName() + ".cancelVL", 5);
    }

    protected boolean shouldCancel() {
        return cancelVL >= 0 && violations >= cancelVL;
    }

    protected SimpleCollisionBox getCombinedBox(final BlockPlace place) {
        // Alright, instead of skidding AACAdditionsPro, let's just use bounding boxes
        ImmutableVector3i clicked = place.getPlacedAgainstBlockLocation();
        CollisionBox placedOn = HitboxData.getBlockHitbox(player, place.getMaterial(), player.getClientVersion(), player.compensatedWorld.getBlock(clicked), true, clicked.getX(), clicked.getY(), clicked.getZ());

        int size = placedOn.downCast(boxes);

        SimpleCollisionBox combined = new SimpleCollisionBox(clicked.getX(), clicked.getY(), clicked.getZ());
        for (int i = 0; i < size; i++) {
            SimpleCollisionBox box = boxes[i];
            double minX = Math.max(box.minX, combined.minX);
            double minY = Math.max(box.minY, combined.minY);
            double minZ = Math.max(box.minZ, combined.minZ);
            double maxX = Math.min(box.maxX, combined.maxX);
            double maxY = Math.min(box.maxY, combined.maxY);
            double maxZ = Math.min(box.maxZ, combined.maxZ);
            combined = new SimpleCollisionBox(minX, minY, minZ, maxX, maxY, maxZ);
        }

        if (weirdBoxes.contains(place.getPlacedAgainstMaterial())) {
            // Invert the box to give lenience
            combined = new SimpleCollisionBox(clicked.getX() + 1, clicked.getY() + 1, clicked.getZ() + 1, clicked.getX(), clicked.getY(), clicked.getZ());
        }

        if (buggyBoxes.contains(place.getPlacedAgainstMaterial())) {
            // Invert the bounding box to give a block of lenience
            combined = new SimpleCollisionBox(clicked.getX() + 1, clicked.getY() + 1, clicked.getZ() + 1, clicked.getX(), clicked.getY(), clicked.getZ());
        }

        return combined;
    }
}
