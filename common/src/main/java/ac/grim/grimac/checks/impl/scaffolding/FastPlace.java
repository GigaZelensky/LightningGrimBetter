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
 * FastPlace – detects abnormally quick and consistent block-placement packets.
 *
 * Strategy
 * • Two independent timing windows (PLACEMENT / USE).  
 * • Adaptive outlier gate keeps statistical noise realistic.  
 * • Dynamic CoV limit tightens as average interval drops.  
 * • Physiological floor (<15 ms) flags impossible speeds.  
 * • Exhaustion ramp relaxes limit during long high-CPS runs.  
 * • Buffered verdict: only sustained breaches raise a violation.
 */
@CheckData(name = "FastPlace", experimental = true)
public class FastPlace extends Check implements PacketCheck {

    private static final int  WINDOW          = 15;
    private static final long MIN_DELTA_NS    = 4_000_000L;      // 4 ms  – duplicate filter
    private static final long MAX_GAP_NS      = 200_000_000L;    // 200 ms – window reset
    private static final long MAX_FLAG_AVG_NS = 150_000_000L;    // 150 ms – slowest we judge
    private static final long P1_NS           = 60_000_000L;     //  60 ms – model pivot
    private static final long MIN_STD_NS      = 4_000_000L;      //   4 ms – σ floor

    /* outlier factors */
    private static final double FAST_FACTOR   = 0.60D;
    private static final double SLOW_FACTOR   = 3.0D;

    /* physiological floor */
    private static final long  FLOOR_NS       = 15_000_000L;     // 15 ms ≈ 66 cps
    private static final int   FLOOR_WINDOW   = 6;
    private static final int   FLOOR_HITS_MAX = 4;

    /* exhaustion */
    private static final long   FAST_THRESHOLD_NS = 59_000_000L; // ≥17 cps
    private static final long   EXHAUST_START_NS  = 1_000_000_000L;
    private static final long   EXHAUST_FULL_NS   = 12_000_000_000L;
    private static final double MAX_DEVIATION_LIM = 0.80D;

    /* decision buffer */
    private static final int BUFFER_MAX = 6;

    /* per-stream state */
    private final Deque<Long>    placeDeltas = new ArrayDeque<>(WINDOW);
    private final Deque<Long>     useDeltas  = new ArrayDeque<>(WINDOW);
    private final Deque<Boolean> placeFloor  = new ArrayDeque<>(FLOOR_WINDOW);
    private final Deque<Boolean>  useFloor   = new ArrayDeque<>(FLOOR_WINDOW);
    private long lastPlaceTime = -1L, placeFastStart = -1L;
    private long  lastUseTime  = -1L,  useFastStart  = -1L;
    private int   placeBuf = 0,        useBuf  = 0;

    public FastPlace(@NotNull GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        final long now = System.nanoTime();

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            handleStream(now, placeDeltas, placeFloor, true,  event);
        } else if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            handleStream(now, useDeltas,  useFloor,  false, event);
        }
    }

    /* ---------- per-stream processing ---------- */
    private void handleStream(long now,
                              Deque<Long> deltas,
                              Deque<Boolean> floorTrack,
                              boolean isPlacement,
                              PacketReceiveEvent event) {

        long lastTime  = isPlacement ? lastPlaceTime  : lastUseTime;
        long fastStart = isPlacement ? placeFastStart : useFastStart;

        if (lastTime != -1L) {
            long deltaNs = now - lastTime;
            if (deltaNs <= 0L || deltaNs < MIN_DELTA_NS) return;

            if (deltaNs > MAX_GAP_NS) {
                deltas.clear(); floorTrack.clear();
                if (isPlacement) { lastPlaceTime = now; placeFastStart = -1L; }
                else             {  lastUseTime  = now;  useFastStart  = -1L; }
                return;
            }

            /* push interval (keep outliers for σ) */
            if (deltas.size() == WINDOW) deltas.removeFirst();
            deltas.add(deltaNs);

            /* classify outlier */
            boolean outlier = false;
            if (deltas.size() >= 5) {
                double med = medianNs(deltas);
                outlier = (deltaNs < med * FAST_FACTOR) || (deltaNs > med * SLOW_FACTOR);
            }

            /* physiological floor */
            if (!outlier) {
                boolean floorHit = deltaNs < FLOOR_NS;
                if (floorTrack.size() == FLOOR_WINDOW) floorTrack.removeFirst();
                floorTrack.add(floorHit);

                if (countTrue(floorTrack) >= FLOOR_HITS_MAX) {
                    String tag = isPlacement ? "PLACEMENT" : "USE";
                    if (flagAndAlert(tag + " <15 ms floor breach") && shouldModifyPackets()) {
                        event.setCancelled(true); player.onPacketCancel();
                    }
                    floorTrack.clear();
                }
            }

            /* exhaustion */
            if (!outlier) {
                if (deltaNs <= FAST_THRESHOLD_NS) {
                    if (fastStart == -1L) fastStart = now;
                } else fastStart = -1L;
            }
        }

        if (isPlacement) { lastPlaceTime = now; placeFastStart = fastStart; }
        else             {  lastUseTime  = now;  useFastStart  = fastStart; }

        /* decision block when window full */
        if (deltas.size() == WINDOW) {
            double avgNs = average(deltas);
            double stdNs = Math.max(MIN_STD_NS, standardDeviation(deltas, avgNs));
            double cov   = stdNs / avgNs;

            if (avgNs <= MAX_FLAG_AVG_NS) {
                double baseLimit;
                if (avgNs <= P1_NS) baseLimit = 0.45D - 0.10D * (avgNs / (double) P1_NS);
                else                baseLimit = 0.30D - 0.20D *
                                            ((avgNs - P1_NS) / (double) (MAX_FLAG_AVG_NS - P1_NS));
                baseLimit = Math.max(baseLimit, 0.15D);

                long fs = fastStart;
                double effLimit = baseLimit;
                if (fs != -1L) {
                    long runNs = now - fs;
                    if (runNs > EXHAUST_START_NS) {
                        double t = Math.min(1D, (double) (runNs - EXHAUST_START_NS) /
                                                 (double) (EXHAUST_FULL_NS - EXHAUST_START_NS));
                        effLimit += (MAX_DEVIATION_LIM - effLimit) * t;
                    }
                }

                /* -------- buffered verdict -------- */
                boolean breach = cov < effLimit;
                int buf = isPlacement ? placeBuf : useBuf;

                if (breach) {
                    buf = Math.min(BUFFER_MAX, buf + 1);
                    if (buf >= BUFFER_MAX) {
                        String tag = isPlacement ? "PLACEMENT" : "USE";
                        if (flagAndAlert(String.format(
                                "%s μ=%.2f ms σ=%.2f ms cov=%.3f limit=%.3f",
                                tag, avgNs / 1_000_000D, stdNs / 1_000_000D, cov, effLimit))
                                && shouldModifyPackets()) {
                            event.setCancelled(true); player.onPacketCancel();
                        }
                        buf = 0; // reset after flag
                    }
                } else {
                    buf = Math.max(0, buf - 1);
                }

                if (isPlacement) placeBuf = buf; else useBuf = buf;
            }
        }
    }

    /* ---------------- helpers ---------------- */

    private static double medianNs(Deque<Long> q) {
        int n = q.size();
        long[] a = new long[n]; int i = 0; for (Long v : q) a[i++] = v;
        Arrays.sort(a); return n % 2 == 0 ? (a[n/2-1] + a[n/2]) / 2.0 : a[n/2];
    }

    private static int countTrue(Deque<Boolean> q) {
        int c = 0; for (Boolean b : q) if (b) c++; return c;
    }

    private static double average(Iterable<Long> v) {
        long s = 0L; int n = 0; for (Long x : v) { s += x; n++; }
        return n == 0 ? 0D : s / (double) n;
    }

    private static double standardDeviation(Iterable<Long> v, double mean) {
        double var = 0D; int n = 0;
        for (Long x : v) { double d = x - mean; var += d * d; n++; }
        return n == 0 ? 0D : GrimMath.sqrt((float) (var / n));
    }
}