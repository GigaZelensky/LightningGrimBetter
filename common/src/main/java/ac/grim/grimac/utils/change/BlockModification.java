package ac.grim.grimac.utils.change;

import ac.grim.grimac.api.packet.block.PacketBlockState;
import ac.grim.grimac.api.packet.util.vec.ImmutableVector3i;

public record BlockModification(PacketBlockState oldBlockContents,
                                PacketBlockState newBlockContents,
                                ImmutableVector3i location, int tick,
                                Cause cause) {

    @Override
    public String toString() {
        return String.format(
                "BlockModification{location=%s, old=%s, new=%s, tick=%d, cause=%s}",
                location, oldBlockContents, newBlockContents, tick, cause
        );
    }

    public enum Cause {
        START_DIGGING,
        APPLY_BLOCK_CHANGES,
        HANDLE_NETTY_SYNC_TRANSACTION,
        OTHER
    }
}
