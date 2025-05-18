package ac.grim.grimac.utils.data;

import ac.grim.grimac.api.packet.util.vec.ImmutableVector3i;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
@AllArgsConstructor
public class MainSupportingBlockData {
    @Nullable
    ImmutableVector3i blockPos;
    boolean onGround;

    public boolean lastOnGroundAndNoBlock() {
        return blockPos == null && onGround;
    }
}
