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
 * This version uses nano time for accuracy, ignores large gaps and evaluates
 * both average and variation of recent place intervals to minimize false flags.
 */
@CheckData(name = "FastPlace", experimental = true)
public class FastPlace extends Check implements PacketCheck {

    private static final int WINDOW = 15;
    private final Deque<Long> deltas = new ArrayDeque<>(WINDOW);
    private long lastTime = -1L;
    private long lastDelta = -1L;

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
            long deltaMs = (now - lastTime) / 1_000_000L;
            lastTime = now;

            if (deltaMs <= 0L || deltaMs > 200L) {
                deltas.clear();
                return;
            }

            if (deltaMs != lastDelta) {
                if (deltas.size() == WINDOW) {
                    deltas.removeFirst();
                }
                deltas.add(deltaMs);
                lastDelta = deltaMs;
            }

            if (deltas.size() == WINDOW) {
                double avg = average(deltas);
                double std = standardDeviation(deltas, avg);
                double cov = std / avg;

                if (avg < 60D && cov < 0.35D) {
                    if (flagAndAlert(String.format("\u03bc=%.2fms \u03c3=%.2fms cov=%.3f", avg, std, cov))
                            && shouldModifyPackets()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                    }
                    deltas.clear();
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

