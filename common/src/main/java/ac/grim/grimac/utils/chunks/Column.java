package ac.grim.grimac.utils.chunks;


import ac.grim.grimac.api.packet.world.chunk.PacketChunk;

public record Column(int x, int z, PacketChunk[] chunks, int transaction) {

    // This ability was removed in 1.17 because of the extended world height
    // Therefore, the size of the chunks are ALWAYS 16!
    public void mergeChunks(PacketChunk[] toMerge) {
        for (int i = 0; i < 16; i++) {
            if (toMerge[i] != null) chunks[i] = toMerge[i];
        }
    }
}
