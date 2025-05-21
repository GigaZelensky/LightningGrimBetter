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
 * Uses nano-time for all math; dynamic covariance limit scales with average speed.
 * <p>
 * 2025-05-21 tweaks #2  
 * • Two independent windows: one for PLAYER_BLOCK_PLACEMENT, one for USE_ITEM.  
 * • Sub-tick duplicates (< 4 ms) are ignored per stream.  
 * • Everything else (curve, alerts, formatting) unchanged.
 */
@CheckData(name = "FastPlace", experimental = true)
public class FastPlace extends Check implements PacketCheck {

    private static final int  WINDOW          = 15;
    private static final long MIN_DELTA_NS    = 4_000_000L;    // 4 ms – skip duplicates
    private static final long MAX_GAP_NS      = 200_000_000L;  // 200 ms – hard reset
    private static final long MAX_FLAG_AVG_NS = 150_000_000L;  // 150 ms
    private static final long P1_NS           = 60_000_000L;   //  60 ms

    /* independent streams */
    private final Deque<Long> placeDeltas = new ArrayDeque<>(WINDOW);
    private final Deque<Long>  useDeltas  = new ArrayDeque<>(WINDOW);
    private long lastPlaceTime = -1L;
    private long  lastUseTime  = -1L;

    public FastPlace(@NotNull GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        final long now = System.nanoTime();

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            handleStream(now, placeDeltas, true, event);
        } else if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            handleStream(now, useDeltas, false, event);
        }
    }

    /* ---------- per-stream logic ---------- */
    private void handleStream(long now,
                              Deque<Long> deltas,
                              boolean isPlacement,
                              PacketReceiveEvent event) {

        long lastTime = isPlacement ? lastPlaceTime : lastUseTime;

        if (lastTime != -1L) {
            long deltaNs = now - lastTime;

            if (deltaNs <= 0L) return;                 // clock anomaly
            if (deltaNs < MIN_DELTA_NS) return;        // duplicate inside same tick

            if (deltaNs > MAX_GAP_NS) {                // long gap → reset
                deltas.clear();
                if (isPlacement) lastPlaceTime = now; else lastUseTime = now;
                return;
            }

            if (deltas.size() == WINDOW) {
                deltas.removeFirst();
            }
            deltas.add(deltaNs);
        }

        if (isPlacement) lastPlaceTime = now; else lastUseTime = now;

        if (deltas.size() == WINDOW) {
            double avgNs = average(deltas);
            double stdNs = standardDeviation(deltas, avgNs);
            double cov   = stdNs / avgNs;

            if (avgNs <= MAX_FLAG_AVG_NS) {
                double covLimit;
                if (avgNs <= P1_NS) {
                    covLimit = 0.45D - 0.10D * (avgNs / (double) P1_NS);
                } else {
                    covLimit = 0.35D - 0.20D *
                               ((avgNs - P1_NS) / (double) (MAX_FLAG_AVG_NS - P1_NS));
                }
                covLimit = Math.max(covLimit, 0.15D);

                if (cov < covLimit) {
                    String tag = isPlacement ? "PLACEMENT" : "USE";
                    if (flagAndAlert(String.format(
                            "%s \u03bc=%.2fms \u03c3=%.2fms cov=%.3f limit=%.3f",
                            tag, avgNs / 1_000_000D, stdNs / 1_000_000D, cov, covLimit))
                            && shouldModifyPackets()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                    }
                }
            }
        }
    }

    /* ---------- simple stats helpers ---------- */

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