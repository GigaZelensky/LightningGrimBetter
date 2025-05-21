package ac.grim.grimac.checks.impl.scaffolding;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.math.GrimMath;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * FastPlace
 * ────────────────────────────────────────────────────────────────────────────────
 * An ensemble timing detector for block placement and use-item packets.
 *
 * • 15-sample core window – dynamic CoV curve with σ floor (4 ms).  
 * • 30-sample auxiliary view – additional macro heuristics:
 *   - distinct delay count, range spread, CPS-rounding, hard CPS + CoV gate.  
 * • Physiological floor: <15 ms intervals flag after 4/6 hits.  
 * • Exhaustion ramp: sustained ≥17 cps lowers allowed CoV toward 0.80.  
 * • Outlier guard: side-effects ignored for Δ <0.6× median or >3×.  
 * • Simple buffer: +1 on failure, −0.25 on pass, trip at 5 (packet cancelled).
 * ────────────────────────────────────────────────────────────────────────────────
 */
@CheckData(name = "FastPlace", experimental = true)
public class FastPlace extends Check implements PacketCheck {

    /* ---------- constants ---------- */

    private static final int  WINDOW          = 15;
    private static final int  LONG_WINDOW     = 30;
    private static final long MIN_DELTA_NS    = 4_000_000L;
    private static final long MAX_GAP_NS      = 200_000_000L;
    private static final long MAX_FLAG_AVG_NS = 150_000_000L;
    private static final long P1_NS           = 60_000_000L;
    private static final long MIN_STD_NS      = 4_000_000L;
    private static final double BUFFER_DECAY  = 0.25D;
    private static final double BUFFER_HARD   = 5.0D;

    private static final double FAST_FACTOR = 0.60D, SLOW_FACTOR = 3.0D;

    private static final long FLOOR_NS = 15_000_000L;
    private static final int  FLOOR_WINDOW = 6, FLOOR_HITS_MAX = 4;
    private static final long FAST_THRESHOLD_NS = 59_000_000L;
    private static final long EXHAUST_START_NS  = 1_000_000_000L;
    private static final long EXHAUST_FULL_NS   = 12_000_000_000L;
    private static final double MAX_DEVIATION_LIM = 0.80D;

    private static final int   DISTINCT_MIN   = 6;
    private static final long  RANGE_MIN_NS   = 50_000_000L;
    private static final double CPS_ROUND_EPS = 0.08D;
    private static final double HARD_CPS      = 17.0D;

    /* ---------- state ---------- */

    private final Deque<Long> placeDeltas = new ArrayDeque<>(LONG_WINDOW);
    private final Deque<Long>  useDeltas  = new ArrayDeque<>(LONG_WINDOW);
    private final Deque<Boolean> placeFloor = new ArrayDeque<>(FLOOR_WINDOW);
    private final Deque<Boolean>  useFloor  = new ArrayDeque<>(FLOOR_WINDOW);
    private double buffer = 0D;

    private long lastPlaceTime = -1L, placeFastStart = -1L;
    private long  lastUseTime  = -1L,  useFastStart  = -1L;

    public FastPlace(@NotNull GrimPlayer player) { super(player); }

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

    private void handleStream(long now, Deque<Long> deltas,
                              Deque<Boolean> floorTrack,
                              boolean isPlacement,
                              PacketReceiveEvent event) {

        long lastTime  = isPlacement ? lastPlaceTime  : lastUseTime;
        long fastStart = isPlacement ? placeFastStart : useFastStart;

        /* sample intake, gap reset, outlier tagging */
        if (lastTime != -1L) {
            long deltaNs = now - lastTime;
            if (deltaNs <= 0L || deltaNs < MIN_DELTA_NS) return;

            if (deltaNs > MAX_GAP_NS) { deltas.clear(); floorTrack.clear(); fastStart = -1L; }
            else {
                if (deltas.size() == LONG_WINDOW) deltas.removeFirst();
                deltas.add(deltaNs);

                boolean outlier = false;
                if (deltas.size() >= 5) {
                    double med = medianNs(deltas);
                    outlier = (deltaNs < med * FAST_FACTOR) || (deltaNs > med * SLOW_FACTOR);
                }

                if (!outlier) {
                    boolean floorHit = deltaNs < FLOOR_NS;
                    if (floorTrack.size() == FLOOR_WINDOW) floorTrack.removeFirst();
                    floorTrack.add(floorHit);
                    if (countTrue(floorTrack) >= FLOOR_HITS_MAX) {
                        tagAndCancel(isPlacement, " <15ms floor breach", event);
                        floorTrack.clear();
                    }

                    if (deltaNs <= FAST_THRESHOLD_NS) { if (fastStart == -1L) fastStart = now; }
                    else fastStart = -1L;
                }
            }
        }

        if (isPlacement) { lastPlaceTime = now; placeFastStart = fastStart; }
        else             {  lastUseTime = now;  useFastStart  = fastStart; }

        /* main statistics */
        if (deltas.size() >= WINDOW) {
            List<Long> view15 = lastN(deltas, WINDOW);
            double avgNs = average(view15);
            double stdNs = standardDeviation(view15, avgNs);
            if (stdNs < MIN_STD_NS) stdNs = MIN_STD_NS;
            double cov = stdNs / avgNs;

            double baseLimit = (avgNs <= P1_NS)
                    ? 0.45D - 0.10D * (avgNs / (double) P1_NS)
                    : 0.30D - 0.20D * ((avgNs - P1_NS) / (double) (MAX_FLAG_AVG_NS - P1_NS));
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

            /* ensemble tests */
            List<Long> view30 = deltas.size() >= LONG_WINDOW ? lastN(deltas, LONG_WINDOW) : view15;
            boolean distinctFail  = distinct(view30) < DISTINCT_MIN;
            boolean rangeFail     = (Collections.max(view30) - Collections.min(view30)) < RANGE_MIN_NS;
            double cps15          = 1_000_000_000D / avgNs;
            boolean cpsRoundFail  = Math.abs(Math.round(cps15) - cps15) < CPS_ROUND_EPS;
            boolean hardCpsFail   = cps15 >= HARD_CPS && cov < effLimit;

            boolean mainFail = cov < effLimit;
            boolean subFail  = distinctFail || rangeFail || cpsRoundFail || hardCpsFail;

            buffer = mainFail || subFail ? buffer + 1D : Math.max(0D, buffer - BUFFER_DECAY);

            if (buffer >= BUFFER_HARD) {
                tagAndCancel(isPlacement,
                        String.format(" μ=%.2fms σ=%.2fms cov=%.3f buf=%.1f", avgNs/1e6, stdNs/1e6, cov, buffer), event);
                buffer = 0D;
            }
        }
    }

    /* ---------- helpers ---------- */

    private void tagAndCancel(boolean placement, String msg, PacketReceiveEvent e) {
        if (flagAndAlert((placement ? "PLACEMENT" : "USE") + msg) && shouldModifyPackets()) {
            e.setCancelled(true); player.onPacketCancel();
        }
    }

    private static List<Long> lastN(Deque<Long> q, int n) {
        Long[] arr = q.toArray(new Long[0]);
        return Arrays.asList(Arrays.copyOfRange(arr, arr.length - n, arr.length));
    }

    private static int countTrue(Deque<Boolean> q) { int c = 0; for (Boolean b : q) if (b) c++; return c; }

    private static double medianNs(Collection<Long> c) {
        long[] a = c.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(a); int n = a.length;
        return n % 2 == 0 ? (a[n / 2 - 1] + a[n / 2]) / 2.0 : a[n / 2];
    }

    private static int distinct(Collection<Long> c) { return new HashSet<>(c).size(); }

    private static double average(Collection<Long> v) {
        return v.stream().mapToLong(Long::longValue).average().orElse(0D);
    }

    private static double standardDeviation(Collection<Long> v, double mean) {
        double var = 0D; int n = 0;
        for (long x : v) { double d = x - mean; var += d * d; n++; }
        return n == 0 ? 0D : GrimMath.sqrt((float) (var / n));
    }
}