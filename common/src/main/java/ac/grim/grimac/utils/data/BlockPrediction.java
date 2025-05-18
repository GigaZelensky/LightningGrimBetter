package ac.grim.grimac.utils.data;

import ac.grim.grimac.api.packet.util.vec.ImmutableVector3d;
import ac.grim.grimac.api.packet.util.vec.ImmutableVector3i;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@Getter
@Setter
public class BlockPrediction {
    List<ImmutableVector3i> forBlockUpdate;
    ImmutableVector3i blockPosition;
    int originalBlockId;
    ImmutableVector3d playerPosition;
}
