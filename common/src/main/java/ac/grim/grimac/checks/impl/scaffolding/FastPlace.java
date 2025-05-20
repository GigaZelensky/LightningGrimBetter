package ac.grim.grimac.checks.impl.scaffolding;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.math.GrimMath;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * FastPlace – detects abnormally quick and consistent block placement packets.
 * <p>
 * Uses nano-time for all math; thresholds converted to nanoseconds.
 */
@CheckData(name = "FastPlace", experimental = true)
public class FastPlace extends Check implements PacketCheck {

    private static final int WINDOW = 15;
    private static final long MAX_GAP_NS   = 200_000_000L;   // 200 ms
    private static final double MAX_AVG_NS = 60_000_000D;    // 60 ms

    private final Deque<Long> deltas = new ArrayDeque<>(WINDOW);
    private long lastTime = -1L;

    public FastPlace(@NotNull GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT
                && event.getPacketType() != PacketType.Play.Client.USE_ITEM) {
            return;
        }

        final long now = System.nanoTime();
        if (lastTime != -1L) {
            long deltaNs = now - lastTime;
            lastTime = now;

            if (deltaNs <= 0L || deltaNs > MAX_GAP_NS) {
                return;
            }

            if (deltas.size() == WINDOW) {
                deltas.removeFirst();
            }
            deltas.add(deltaNs);

            if (deltas.size() == WINDOW) {
                double avgNs = average(deltas);
                double stdNs = standardDeviation(deltas, avgNs);
                double cov = stdNs / avgNs;

                if (avgNs < MAX_AVG_NS && cov < 0.35D) {
                    if (flagAndAlert(String.format("\u03bc=%.2fms \u03c3=%.2fms cov=%.3f",
                                    avgNs / 1_000_000D, stdNs / 1_000_000D, cov))
                            && shouldModifyPackets()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                    }
                }
            }
        } else {
            lastTime = now;
        }
    }

    private static double average(Iterable<Long> values) {
        long sum = 0L;
        int count = 0;
        for (Long v : values) {
            sum += v;
            count++;
        }
        return count == 0 ? 0D : sum / (double) count;
    }

    private static double standardDeviation(Iterable<Long> values, double mean) {
        double var = 0D;
        int count = 0;
        for (Long v : values) {
            double diff = v - mean;
            var += diff * diff;
            count++;
        }
        return count == 0 ? 0D : GrimMath.sqrt((float) (var / count));
    }
}