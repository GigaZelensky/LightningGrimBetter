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
 * FastPlace – production build
 *
 * Behaviour
 * ─────────
 * • Two independent 15-sample timing windows (PLACEMENT / USE).
 * • Δ-domain CoV limit: 0.50 @ 1 ms → 0.35 @ 60 ms → 0.15 @ 150 ms.
 * • σ(Cov) “cov-stability” starts after 15 samples:
 *     0.50 @ 1 ms → 0.050 @ 65 ms → 0.005 @ 150 ms (quadratic).
 * • Floor protection: window-average <35 ms (but ≥1 ms) for 4 of 6
 *   consecutive windows → instant flag.
 * • Exhaustion: linear 5 s (≤55 ms) to 7 s (75 ms), disabled >95 ms,
 *   based on streak average while ≤95 ms.
 * • σ-stability on raw deltas (±3 %) flags after 150 packets.
 * • Six-tick buffer before any cancellation.
 * • Debug stream gated by “grim.debug.fastplace”.
 */
@CheckData(name = "FastPlace", experimental = true)
public class FastPlace extends Check implements PacketCheck {

    private static final int  WINDOW          = 15;
    private static final long MAX_GAP_NS      = 200_000_000L;   // 200 ms
    private static final long MAX_FLAG_AVG_NS = 150_000_000L;   // 150 ms
    private static final long P1_NS           = 60_000_000L;    //  60 ms
    private static final long MIN_STD_NS      = 4_000_000L;     //   4 ms
    private static final long MIN_COV_NS      = 1_000_000L;     //   1 ms

    /* floor uses window-average */
    private static final long FLOOR_NS       = 35_000_000L;     // 35 ms
    private static final int  FLOOR_WINDOW   = 6;
    private static final int  FLOOR_HITS_MAX = 4;

    private static final long FAST_STREAK_NS = 95_000_000L;     // ≤95 ms
    private static final long CPS19_NS       = 53_000_000L;     // ≈19 CPS

    private static final long   EXH_START_NS = 1_000_000_000L;  // 1 s
    private static final long   EXH_FULL_NS  = 12_000_000_000L; // 12 s
    private static final double MAX_DEVIATION_LIM = 0.80D;

    private static final int BUFFER_MAX = 6;
    private static final int SIGMA_STABLE_TARGET = 150;
    private static final double SIGMA_STABLE_BAND = 0.03D;      // ±3 %

    private final Deque<Long>    placeDeltas = new ArrayDeque<>(WINDOW);
    private final Deque<Long>     useDeltas  = new ArrayDeque<>(WINDOW);
    private final Deque<Boolean> placeFloor  = new ArrayDeque<>(FLOOR_WINDOW);
    private final Deque<Boolean>  useFloor   = new ArrayDeque<>(FLOOR_WINDOW);
    private final Deque<Double>  placeCovSeries = new ArrayDeque<>(WINDOW);
    private final Deque<Double>   useCovSeries  = new ArrayDeque<>(WINDOW);

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
            handle(now, placeDeltas, placeFloor, placeCovSeries, true, event);
        } else if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            handle(now, useDeltas,  useFloor,  useCovSeries,  false, event);
        }
    }

    /* ---------------- core ---------------- */
    private void handle(long now,
                        Deque<Long> deltas,
                        Deque<Boolean> floorTrack,
                        Deque<Double> covSeries,
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

            if (deltaNs > MAX_GAP_NS) {          // window reset
                deltas.clear(); covSeries.clear(); floorTrack.clear();
                fastStart = -1L; fastCount = 0L;
                updateState(isPlacement, now, fastStart, fastCount);
                return;
            }

            if (deltas.size() == WINDOW) deltas.removeFirst();
            deltas.add(deltaNs);

            /* fast-streak tracking (≤95 ms) */
            if (deltaNs <= FAST_STREAK_NS) {
                if (fastStart == -1L) { fastStart = now; fastCount = 1; }
                else fastCount++;
            } else { fastStart = -1L; fastCount = 0L; }

            if (debug) player.sendMessage((isPlacement ? "[P] " : "[U] ") +
                                          "Δ=" + deltaNs / 1_000_000D + " ms");
        }

        updateState(isPlacement, now, fastStart, fastCount);

        /* ---------- window evaluation ---------- */
        if (deltas.size() == WINDOW) {
            double avgNs = average(deltas);
            double stdNs = Math.max(MIN_STD_NS, standardDeviation(deltas, avgNs));
            double cov   = stdNs / avgNs;

            /* ---- floor by window-average (<35 ms, but ≥1 ms) ---- */
            boolean floorHit = avgNs >= MIN_COV_NS && avgNs < FLOOR_NS;
            if (floorTrack.size() == FLOOR_WINDOW) floorTrack.removeFirst();
            floorTrack.add(floorHit);
            if (countTrue(floorTrack) >= FLOOR_HITS_MAX &&
                flagAndAlert((isPlacement ? "PLACEMENT" : "USE") +
                             " window-μ <35 ms (4/6)") &&
                shouldModifyPackets()) {
                event.setCancelled(true);
                player.onPacketCancel();
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

            /* ---- exhaustion (streak average) ---- */
            boolean exhaustionAutoFlag = false;
            double streakAvgNs = Double.MAX_VALUE;
            if (fastStart != -1L && fastCount > 0) {
                streakAvgNs = (now - fastStart) / (double) fastCount;
                if (streakAvgNs <= CPS19_NS) {
                    double xMs = streakAvgNs / 1_000_000.0;
                    double tfFlag = xMs <= 55 ? 5 :
                                    xMs <= 75 ? 0.10 * xMs - 0.5 :
                                    xMs <= 95 ? 7.0 + (xMs - 75) * 0.5 :
                                    Double.MAX_VALUE;
                    exhaustionAutoFlag = (now - fastStart) / 1_000_000_000.0 >= tfFlag;
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
                    long runNs = now - fastStart;
                    if (runNs > EXH_START_NS) {
                        double t = Math.min(1D,
                           (double) (runNs - EXH_START_NS) / (EXH_FULL_NS - EXH_START_NS));
                        effLimit += (MAX_DEVIATION_LIM - effLimit) * t;
                    }
                }

                boolean breach = (cov < effLimit) || covStable || exhaustionAutoFlag;
                int buf = isPlacement ? placeBuf : useBuf;

                if (breach) {
                    buf = Math.min(BUFFER_MAX, buf + 1);
                    if (buf >= BUFFER_MAX) {
                        String tag = isPlacement ? "PLACEMENT" : "USE";
                        if (flagAndAlert(String.format(
                                "%s μ=%.2f ms σ=%.2f ms cov=%.3f lim=%.3f σ(cov)=%s<%s EXH=%s",
                                tag, avgNs / 1_000_000D, stdNs / 1_000_000D,
                                cov, effLimit,
                                covReady ? String.format("%.3f", covSigma) : "--",
                                covReady ? String.format("%.3f", covLimit) : "--",
                                exhaustionAutoFlag))
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
            return 0.35D + ratio * 0.15D;
        }
        if (avgNs <= MAX_FLAG_AVG_NS) {
            double ratio = (avgNs - P1_NS)
                         / (double)(MAX_FLAG_AVG_NS - P1_NS);
            return 0.35D - ratio * 0.20D;
        }
        return 0.15D;
    }

    /* σ(Cov) quadratic limit */
    private static double covVarLimit(double avgNs) {

        final long T0_NS = MIN_COV_NS;        //   1 ms
        final long T1_NS = 65_000_000L;       //  65 ms
        final long T2_NS = 150_000_000L;      // 150 ms

        if (avgNs <= T1_NS) {
            double t = (avgNs - T0_NS) / (double) (T1_NS - T0_NS);
            return 0.50D - 0.45D * t * t;     // 0.50 → 0.050
        }

        if (avgNs <= T2_NS) {
            double t = (avgNs - T1_NS) / (double) (T2_NS - T1_NS);
            return 0.050D - 0.045D * t * t;   // 0.050 → 0.005
        }

        return 0.005D;
    }
}