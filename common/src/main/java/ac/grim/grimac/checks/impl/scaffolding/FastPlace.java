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
import java.util.Arrays;
import java.util.Deque;

/**
 * FastPlace – detects abnormally quick and consistent block placement packets.
 * <p>
 * Uses nano-time for all math; dynamic covariance limit scales with average speed.
 * 2025-05-22 quick patch:
 * • Outlier gate no longer skips samples (keeps σ realistic).  
 * • σ clamped to ≥ 4 ms before CoV calc to stop collapse toward zero.
 */
@CheckData(name = "FastPlace", experimental = true)
public class FastPlace extends Check implements PacketCheck {

    private static final int  WINDOW          = 15;
    private static final long MIN_DELTA_NS    = 4_000_000L;      // 4 ms – skip duplicates
    private static final long MAX_GAP_NS      = 200_000_000L;    // 200 ms – hard reset
    private static final long MAX_FLAG_AVG_NS = 150_000_000L;    // 150 ms
    private static final long P1_NS           = 60_000_000L;     //  60 ms
    private static final long MIN_STD_NS      = 4_000_000L;      //  4 ms – σ floor

    /* adaptive outlier filter */
    private static final double FAST_FACTOR   = 0.60D;           // < 60 % median = fast blip
    private static final double SLOW_FACTOR   = 3.0D;            // > 3× median  = slow gap

    /* physiological floor */
    private static final long FLOOR_NS        = 15_000_000L;     // 15 ms  ≈ 66 cps
    private static final int  FLOOR_WINDOW    = 6;               // look-back
    private static final int  FLOOR_HITS_MAX  = 4;               // ≥ 4/6 → flag

    /* exhaustion (continuous high-CPS) */
    private static final long FAST_THRESHOLD_NS   = 59_000_000L; // 17 cps
    private static final long EXHAUST_START_NS    = 1_000_000_000L;  // 1 s
    private static final long EXHAUST_FULL_NS     = 12_000_000_000L; // 12 s
    private static final double MAX_DEVIATION_LIM = 0.80D;       // cap

    /* independent streams */
    private final Deque<Long> placeDeltas = new ArrayDeque<>(WINDOW);
    private final Deque<Long>  useDeltas  = new ArrayDeque<>(WINDOW);
    private final Deque<Boolean> placeFloor = new ArrayDeque<>(FLOOR_WINDOW);
    private final Deque<Boolean>  useFloor  = new ArrayDeque<>(FLOOR_WINDOW);

    private long lastPlaceTime = -1L, placeFastStart = -1L;
    private long  lastUseTime  = -1L,  useFastStart  = -1L;

    public FastPlace(@NotNull GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        final long now = System.nanoTime();

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            handleStream(now, placeDeltas, placeFloor, true, event);
        } else if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            handleStream(now, useDeltas, useFloor, false, event);
        }
    }

    /* ---------- per-stream core ---------- */
    private void handleStream(long now,
                              Deque<Long> deltas,
                              Deque<Boolean> floorTrack,
                              boolean isPlacement,
                              PacketReceiveEvent event) {

        long lastTime   = isPlacement ? lastPlaceTime   : lastUseTime;
        long fastStart  = isPlacement ? placeFastStart  : useFastStart;

        if (lastTime != -1L) {
            long deltaNs = now - lastTime;

            if (deltaNs <= 0L || deltaNs < MIN_DELTA_NS) return;

            if (deltaNs > MAX_GAP_NS) {
                deltas.clear(); floorTrack.clear();
                if (isPlacement) { lastPlaceTime = now; placeFastStart = -1L; }
                else             {  lastUseTime  = now;  useFastStart  = -1L; }
                return;
            }

            /* add interval first (outliers kept for σ) */
            if (deltas.size() == WINDOW) deltas.removeFirst();
            deltas.add(deltaNs);

            /* --- adaptive outlier check – only affects floor/exhaust bookkeeping --- */
            boolean outlier = false;
            if (deltas.size() >= 5) {
                double med = medianNs(deltas);
                outlier = (deltaNs < med * FAST_FACTOR) || (deltaNs > med * SLOW_FACTOR);
            }

            /* floor bookkeeping (ignore outliers) */
            if (!outlier) {
                boolean floorHit = deltaNs < FLOOR_NS;
                if (floorTrack.size() == FLOOR_WINDOW) floorTrack.removeFirst();
                floorTrack.add(floorHit);

                if (countTrue(floorTrack) >= FLOOR_HITS_MAX) {
                    String tag = isPlacement ? "PLACEMENT" : "USE";
                    if (flagAndAlert(tag + " <15ms floor breach") && shouldModifyPackets()) {
                        event.setCancelled(true); player.onPacketCancel();
                    }
                    floorTrack.clear();
                }
            }

            /* exhaustion tracking (ignore outliers) */
            if (!outlier) {
                if (deltaNs <= FAST_THRESHOLD_NS) {
                    if (fastStart == -1L) fastStart = now;
                } else {
                    fastStart = -1L;
                }
            }
        }

        if (isPlacement) { lastPlaceTime = now; placeFastStart = fastStart; }
        else             {  lastUseTime  = now;  useFastStart  = fastStart; }

        if (deltas.size() == WINDOW) {
            double avgNs = average(deltas);
            double stdNs = standardDeviation(deltas, avgNs);
            if (stdNs < MIN_STD_NS) stdNs = MIN_STD_NS;          // σ floor
            double cov   = stdNs / avgNs;

            if (avgNs <= MAX_FLAG_AVG_NS) {
                double baseLimit;
                if (avgNs <= P1_NS) {
                    baseLimit = 0.45D - 0.10D * (avgNs / (double) P1_NS);
                } else {
                    baseLimit = 0.30D - 0.20D *
                                ((avgNs - P1_NS) / (double) (MAX_FLAG_AVG_NS - P1_NS));
                }
                baseLimit = Math.max(baseLimit, 0.15D);

                long fs = fastStart;
                double effLimit = baseLimit;
                if (fs != -1L) {
                    long runNs = now - fs;
                    if (runNs > EXHAUST_START_NS) {
                        double t = (double) (runNs - EXHAUST_START_NS) /
                                   (double) (EXHAUST_FULL_NS - EXHAUST_START_NS);
                        if (t > 1D) t = 1D;
                        effLimit += (MAX_DEVIATION_LIM - effLimit) * t;
                    }
                }

                if (cov < effLimit) {
                    String tag = isPlacement ? "PLACEMENT" : "USE";
                    if (flagAndAlert(String.format(
                            "%s μ=%.2fms σ=%.2fms cov=%.3f limit=%.3f",
                            tag, avgNs / 1_000_000D, stdNs / 1_000_000D, cov, effLimit))
                            && shouldModifyPackets()) {
                        event.setCancelled(true); player.onPacketCancel();
                    }
                }
            }
        }
    }

    /* ---------- helpers ---------- */

    private static double medianNs(Deque<Long> q) {
        int n = q.size();
        long[] a = new long[n]; int i = 0;
        for (Long v : q) a[i++] = v;
        Arrays.sort(a);
        return n % 2 == 0 ? (a[n / 2 - 1] + a[n / 2]) / 2.0 : a[n / 2];
    }

    private static int countTrue(Deque<Boolean> q) {
        int c = 0;
        for (Boolean b : q) if (b) c++;
        return c;
    }

    private static double average(Iterable<Long> v) {
        long s = 0L; int n = 0;
        for (Long x : v) { s += x; n++; }
        return n == 0 ? 0D : s / (double) n;
    }

    private static double standardDeviation(Iterable<Long> v, double mean) {
        double var = 0D; int n = 0;
        for (Long x : v) { double d = x - mean; var += d * d; n++; }
        return n == 0 ? 0D : GrimMath.sqrt((float) (var / n));
    }
}