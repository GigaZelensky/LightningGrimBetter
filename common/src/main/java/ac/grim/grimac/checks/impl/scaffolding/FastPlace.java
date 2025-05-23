package ac.grim.grimac.checks.impl.scaffolding;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.math.GrimMath;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * FastPlace – production build
 *
 * Behaviour
 * ─────────
 * • Two independent 15-sample timing windows (PLACEMENT / USE).
 * • Δ-domain CoV limit: 0.50 @ 1 ms → 0.35 @ 60 ms → 0.15 @ 150 ms.
 * • σ(Cov) "cov-stability" starts after 15 samples:
 *     0.50 @ 1 ms → 0.050 @ 65 ms → 0.005 @ 150 ms (quadratic).
 * • Floor protection: window-average <35 ms (but ≥1 ms) for 4 of 6
 *   consecutive windows → instant flag.
 * • Exhaustion: dynamic – ≥12 CPS flags after ≤10 s,
 *   16 CPS ≈ 6.5 s, 20 CPS ≈ 3 s (resets if 4 / 6 packets are slow).
 * • **Dynamic buffer** – the stricter (lower) the CoV and σ(Cov),
 *   the faster the buffer fills (up to +4 per window).
 * • σ-stability on raw deltas (±3 %) flags after 150 packets.
 * • Six-tick buffer before any cancellation.
 * • Debug stream gated by "grim.debug.fastplace".
 */
@CheckData(name = "FastPlace", experimental = true)
public class FastPlace extends Check implements PacketCheck {

    private static final int  WINDOW          = 15;
    private static final long MAX_GAP_NS      = 300_000_000L;   // 300 ms
    private static final long MAX_FLAG_AVG_NS = 150_000_000L;   // 150 ms
    private static final long P1_NS           = 60_000_000L;    //  60 ms
    private static final long MIN_STD_NS      = 4_000_000L;     //   4 ms
    private static final long MIN_COV_NS      = 1_000_000L;     //   1 ms

    /* floor uses window-average */
    private static final long FLOOR_NS       = 35_000_000L;     // 35 ms
    private static final int  FLOOR_WINDOW   = 6;
    private static final int  FLOOR_HITS_MAX = 4;

    /* dynamic exhaustion */
    private static final long   THRESHOLD_NS_12CPS = 83_000_000L;  // ≈12 CPS
    private static final double EXH_CPS_MIN        = 12.0D;
    private static final double EXH_TIME_INTERCEPT = 20.5D;        // seconds
    private static final double EXH_TIME_SLOPE     = -0.875D;      // sec·CPS⁻¹
    private static final double EXH_TIME_MIN       = 3.0D;         // seconds
    private static final int    FAST_WINDOW_SIZE   = 8;
    private static final int    FAST_WINDOW_SLOW_RESET = 7;

    private static final int BUFFER_MAX = 6;
    private static final int SIGMA_STABLE_TARGET = 150;
    private static final double SIGMA_STABLE_BAND = 0.03D;      // ±3 %

    private final Deque<Long>    placeDeltas = new ArrayDeque<>(WINDOW);
    private final Deque<Long>     useDeltas  = new ArrayDeque<>(WINDOW);
    private final Deque<Boolean> placeFloor  = new ArrayDeque<>(FLOOR_WINDOW);
    private final Deque<Boolean>  useFloor   = new ArrayDeque<>(FLOOR_WINDOW);
    private final Deque<Double>  placeCovSeries = new ArrayDeque<>(WINDOW);
    private final Deque<Double>   useCovSeries  = new ArrayDeque<>(WINDOW);

    // fast-packet history for exhaustion resets
    private final Deque<Boolean> placeFastWindow = new ArrayDeque<>(FAST_WINDOW_SIZE);
    private final Deque<Boolean>   useFastWindow = new ArrayDeque<>(FAST_WINDOW_SIZE);

    private long placeFastStart = -1L, useFastStart = -1L;
    private long placeFastCount = 0L,  useFastCount = 0L;

    private long lastPlaceTime = -1L, lastUseTime = -1L;
    private int  placeBuf = 0, useBuf = 0;
    private int  placeSigmaStable = 0, useSigmaStable = 0;

    public FastPlace(@NotNull GrimPlayer player) { super(player); }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        long now = System.nanoTime();

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            handle(now, placeDeltas, placeFloor, placeCovSeries,
                   placeFastWindow, true, event);
        } else if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            handle(now, useDeltas,  useFloor,  useCovSeries,
                   useFastWindow,  false, event);
        }
    }

    /* ---------------- core ---------------- */
    private void handle(long now,
                        Deque<Long> deltas,
                        Deque<Boolean> floorTrack,
                        Deque<Double> covSeries,
                        Deque<Boolean> fastWindow,
                        boolean isPlacement,
                        PacketReceiveEvent event) {

        final boolean debug = player.hasPermission("grim.debug.fastplace");

        long lastTime  = isPlacement ? lastPlaceTime  : lastUseTime;
        long fastStart = isPlacement ? placeFastStart : useFastStart;
        long fastCount = isPlacement ? placeFastCount : useFastCount;
        int  sigmaStable = isPlacement ? placeSigmaStable : useSigmaStable;

        /* ---------- delta intake ---------- */
        if (lastTime != -1L) {
            long deltaNs = now - lastTime;
            if (deltaNs <= 0L) return;

            long dynamicGapNs = Math.max(MAX_GAP_NS,
                    (long)(average(deltas.isEmpty() ? List.of(deltaNs) : deltas) * 6));

            if (deltaNs > dynamicGapNs) {
                fastWindow.clear();
                fastStart = -1L; fastCount = 0L;
                deltaNs = dynamicGapNs;
            }

            if (deltas.size() == WINDOW) deltas.removeFirst();
            deltas.add(deltaNs);

            /* fast-streak tracking (≤12 CPS threshold) */
            boolean isFast = deltaNs <= THRESHOLD_NS_12CPS;
            if (fastWindow.size() == FAST_WINDOW_SIZE) fastWindow.removeFirst();
            fastWindow.add(isFast);

            if (isFast) {
                if (fastStart == -1L) { fastStart = now; fastCount = 1; }
                else fastCount++;
            }

            /* reset if 7/8 slow packets */
            if (fastWindow.size() == FAST_WINDOW_SIZE) {
                int slow = FAST_WINDOW_SIZE - countTrue(fastWindow);
                if (slow >= FAST_WINDOW_SLOW_RESET) {
                    fastStart = -1L; fastCount = 0L; fastWindow.clear();
                }
            }

            if (debug) player.sendMessage((isPlacement ? "[P] " : "[U] ") +
                                          "Δ=" + deltaNs / 1_000_000D + " ms");
        }

        updateState(isPlacement, now, fastStart, fastCount);

        /* ---------- window evaluation ---------- */
        if (deltas.size() == WINDOW) {
            double avgNs = average(deltas);
            double stdNs = Math.max(MIN_STD_NS, standardDeviation(deltas, avgNs));
            double cov   = stdNs / avgNs;

            /* ---- item-in-hand inspection ---- */
            ItemStack inHand =
                    player.getInventory().getItemInHand(InteractionHand.MAIN_HAND);
            boolean placeable = inHand != null && !inHand.isEmpty() &&
                                inHand.getType().getPlacedType() != null;

            /* ---- floor by window-average (<35 ms, but ≥1 ms) ---- */
            boolean floorHit = false;
            if (placeable) {
                floorHit = avgNs >= MIN_COV_NS && avgNs < FLOOR_NS;
                if (floorTrack.size() == FLOOR_WINDOW) floorTrack.removeFirst();
                floorTrack.add(floorHit);
                if (countTrue(floorTrack) >= FLOOR_HITS_MAX &&
                    flagAndAlert((isPlacement ? "PLACEMENT" : "USE") +
                                 " window-μ <35 ms (4/6)") &&
                    shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            } else {
                floorTrack.clear();
            }

            /* ---- CoV-series for σ(Cov) ---- */
            if (covSeries.size() == WINDOW) covSeries.removeFirst();
            covSeries.add(cov);

            boolean covReady = covSeries.size() == WINDOW;
            double covSigma  = covReady ? standardDeviation(covSeries, average(covSeries))
                                        : Double.NaN;
            double covLimit  = covReady ? covVarLimit(avgNs) : Double.NaN;
            boolean covStable = covReady && covSigma < covLimit;

            /* ---- σ-stability on raw deltas (±3 %) ---- */
            if (Math.abs(stdNs - (avgNs * SIGMA_STABLE_BAND)) <= avgNs * SIGMA_STABLE_BAND)
                sigmaStable++;
            else sigmaStable = 0;
            if (sigmaStable >= SIGMA_STABLE_TARGET &&
                flagAndAlert((isPlacement ? "PLACEMENT" : "USE") +
                             " σ stable ±3 % for 150 packets") &&
                shouldModifyPackets()) {
                event.setCancelled(true);
                player.onPacketCancel();
            }
            if (isPlacement) placeSigmaStable = sigmaStable; else useSigmaStable = sigmaStable;

            boolean exhaustionAutoFlag = false;
            double streakAvgNs = Double.MAX_VALUE;
            if (fastStart != -1L && fastCount > 0) {
                streakAvgNs = (now - fastStart) / (double) fastCount;
                double streakCps = 1_000_000_000.0D / streakAvgNs;

                if (streakCps >= EXH_CPS_MIN) {
                    double tfFlag = Math.max(EXH_TIME_MIN,
                                             EXH_TIME_INTERCEPT + EXH_TIME_SLOPE * streakCps);
                    exhaustionAutoFlag =
                            (now - fastStart) / 1_000_000_000.0D >= tfFlag;
                }
            }

            if (debug) player.sendMessage((isPlacement ? "[P] " : "[U] ") +
                    String.format("AVG=%.2f ms σ=%.2f ms cov=%.3f σ(cov)=%s<%s streak-μ=%.2f ms",
                                  avgNs / 1_000_000D, stdNs / 1_000_000D, cov,
                                  covReady ? String.format("%.3f", covSigma) : "--",
                                  covReady ? String.format("%.3f", covLimit) : "--",
                                  fastCount > 0 ? streakAvgNs / 1_000_000D : 0.0));

            /* ---- main decision ---- */
            if (avgNs <= MAX_FLAG_AVG_NS || exhaustionAutoFlag) {
                double baseLimit = covBaseLimit(avgNs);

                /* exhaustion widens limit */
                double effLimit = baseLimit;
                if (fastStart != -1L) {
                    double streakCps = 1_000_000_000.0D / streakAvgNs;
                    if (streakCps >= EXH_CPS_MIN) {
                        double tfFlag = Math.max(EXH_TIME_MIN,
                                                 EXH_TIME_INTERCEPT + EXH_TIME_SLOPE * streakCps);
                        double t = Math.min(1D,
                           (now - fastStart) / 1_000_000_000.0D / tfFlag);
                        effLimit += (1.0D - effLimit) * t; // widen smoothly to 1.0
                    }
                }

                boolean breach = (cov < effLimit) || covStable || exhaustionAutoFlag;
                int buf = isPlacement ? placeBuf : useBuf;

                if (breach) {
                    /* ---- dynamic buffer aggressiveness ---- */
                    int incr = 1;
                    if (cov < effLimit * 0.75) incr++;      // far below limit
                    if (cov < effLimit * 0.50) incr++;      // very far
                    if (covStable) incr++;                 // σ(Cov) stable
                    if (covReady && covSigma < covLimit * 0.5) incr++; // ultra-stable
                    incr = Math.min(incr, BUFFER_MAX);

                    buf = Math.min(BUFFER_MAX, buf + incr);
                    if (buf >= BUFFER_MAX) {
                        String tag = isPlacement ? "PLACEMENT" : "USE";
                        String exhTag = exhaustionAutoFlag ? " EXH" : "";
                        if (flagAndAlert(String.format(
                                "%s μ=%.2f ms σ=%.2f ms cov=%.3f lim=%.3f σ(cov)=%s<%s%s",
                                tag, avgNs / 1_000_000D, stdNs / 1_000_000D,
                                cov, effLimit,
                                covReady ? String.format("%.3f", covSigma) : "--",
                                covReady ? String.format("%.3f", covLimit) : "--",
                                exhTag))
                                && shouldModifyPackets()) {
                            event.setCancelled(true);
                            player.onPacketCancel();
                        }
                        buf = 0;
                    }
                } else buf = Math.max(0, buf - 1);

                if (isPlacement) placeBuf = buf; else useBuf = buf;
            }
        }
    }

    /* ---------------- helpers ---------------- */
    private void updateState(boolean placement, long now, long fs, long fc) {
        if (placement) {
            lastPlaceTime = now;
            placeFastStart = fs;
            placeFastCount = fc;
        } else {
            lastUseTime = now;
            useFastStart = fs;
            useFastCount = fc;
        }
    }

    private static int countTrue(Deque<Boolean> q) {
        int c = 0; for (Boolean b : q) if (b) c++; return c;
    }

    private static double average(Iterable<? extends Number> v) {
        double s = 0; int n = 0; for (Number x : v) { s += x.doubleValue(); n++; }
        return n == 0 ? 0D : s / n;
    }

    private static double standardDeviation(Iterable<? extends Number> v, double mean) {
        double var = 0; int n = 0;
        for (Number x : v) { double d = x.doubleValue() - mean; var += d * d; n++; }
        return n == 0 ? 0D : GrimMath.sqrt((float) (var / n));
    }

    /* Δ-domain CoV limit */
    private static double covBaseLimit(double avgNs) {
        if (avgNs <= P1_NS) {
            double ratio = (P1_NS - Math.max(avgNs, MIN_COV_NS))
                         / (double)(P1_NS - MIN_COV_NS);
            return 0.30D + ratio * 0.20D;
        }
        if (avgNs <= MAX_FLAG_AVG_NS) {
            double ratio = (avgNs - P1_NS)
                         / (double)(MAX_FLAG_AVG_NS - P1_NS);
            return 0.30D - ratio * 0.20D;
        }
        return 0.15D;
    }

    /* σ(Cov) quadratic limit */
    private static double covVarLimit(double avgNs) {

        final long T0_NS = 35_000_000L;       //  35 ms
        final long T1_NS = 65_000_000L;       //  65 ms
        final long T2_NS = 150_000_000L;      // 150 ms

        /* flat cap below 35 ms – floor-check handles those */
        if (avgNs <= T0_NS) return 0.06D;     // 0 – 35 ms → 0.06

        if (avgNs <= T1_NS) {
            double t = (avgNs - T0_NS) / (double) (T1_NS - T0_NS);
            return 0.06D - 0.035D * t * t;     // 35 ms → 0.06 ▼ 65 ms → 0.035
        }

        if (avgNs <= T2_NS) {
            double t = (avgNs - T1_NS) / (double) (T2_NS - T1_NS);
            return 0.035D - 0.005D * t * t;    // 65 ms → 0.035 ▼ 150 ms → 0.005
        }

        return 0.005D;
    }
}
