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
 * FastPlace – detects abnormally quick and consistent block-placement packets.
 *
 * Design
 * • Independent timing windows for PLACEMENT / USE.  
 * • Δ-domain CoV limit: 0.50 @ 1 ms → 0.35 @ 60 ms → 0.15 @ 150 ms.  
 * • “CoV-stability” – σ(Cov) of last 15 Cov samples must stay below
 *   a speed-based band (0.50 → 0.10 → 0.05).  
 * • Physiological floor (<15 ms) flags impossible speeds.  
 * • **Exhaustion** – if a ≤95 ms (≥10.5 CPS) streak lasts long enough *and*
 *   its *average* CPS is ≥19, flag (5 s at 55 ms … 7 s at 75 ms, disabled ≥95 ms).  
 * • σ-stability: if σ stays within ±3 % of μ for 150 packets, flag.  
 * • Six-tick buffer reduces burst false-positives.  
 * • Debug gated by “grim.debug.fastplace”.
 */
@CheckData(name = "FastPlace", experimental = true)
public class FastPlace extends Check implements PacketCheck {

    private static final int  WINDOW          = 15;
    private static final long MAX_GAP_NS      = 200_000_000L;     // 200 ms
    private static final long MAX_FLAG_AVG_NS = 150_000_000L;     // 150 ms
    private static final long P1_NS           = 60_000_000L;      //  60 ms
    private static final long MIN_STD_NS      = 4_000_000L;       //   4 ms
    private static final long MIN_COV_NS      = 1_000_000L;       //   1 ms

    private static final long FLOOR_NS       = 15_000_000L;       // 15 ms
    private static final int  FLOOR_WINDOW   = 6;
    private static final int  FLOOR_HITS_MAX = 4;

    private static final long FAST_STREAK_NS = 95_000_000L;       // ≤95 ms (≥10.5 CPS)
    private static final long CPS19_NS       = 53_000_000L;       // ≈19 CPS

    private static final long   EXH_START_NS = 1_000_000_000L;    // 1 s
    private static final long   EXH_FULL_NS  = 12_000_000_000L;   // 12 s
    private static final double MAX_DEVIATION_LIM = 0.80D;

    private static final int BUFFER_MAX = 6;
    private static final int SIGMA_STABLE_TARGET = 150;
    private static final double SIGMA_STABLE_BAND = 0.03D;        // ±3 %

    /* ----- CoV-stability ----- */
    private final Deque<Long>    placeDeltas = new ArrayDeque<>(WINDOW);
    private final Deque<Long>     useDeltas  = new ArrayDeque<>(WINDOW);
    private final Deque<Boolean> placeFloor  = new ArrayDeque<>(FLOOR_WINDOW);
    private final Deque<Boolean>  useFloor   = new ArrayDeque<>(FLOOR_WINDOW);
    private final Deque<Double>  placeCovSeries = new ArrayDeque<>(WINDOW);
    private final Deque<Double>   useCovSeries  = new ArrayDeque<>(WINDOW);

    /* ----- fast-streak tracking ----- */
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
                   true, event);
        } else if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            handle(now, useDeltas,  useFloor,  useCovSeries,
                   false, event);
        }
    }

    /* ---------------- core ---------------- */
    private void handle(long now,
                        Deque<Long> deltas,
                        Deque<Boolean> floorTrack,
                        Deque<Double> covSeries,
                        boolean isPlacement,
                        PacketReceiveEvent event) {

        boolean debug = player.hasPermission("grim.debug.fastplace");

        long lastTime  = isPlacement ? lastPlaceTime  : lastUseTime;
        long fastStart = isPlacement ? placeFastStart : useFastStart;
        long fastCount = isPlacement ? placeFastCount : useFastCount;
        int  sigmaStable = isPlacement ? placeSigmaStable : useSigmaStable;

        /* ---------- delta intake ---------- */
        if (lastTime != -1L) {
            long deltaNs = now - lastTime;
            if (deltaNs <= 0L) return;

            /* gap reset */
            if (deltaNs > MAX_GAP_NS) {
                deltas.clear(); floorTrack.clear(); covSeries.clear();
                fastStart = -1L; fastCount = 0L;
                updateState(isPlacement, now, fastStart, fastCount);
                return;
            }

            if (deltas.size() == WINDOW) deltas.removeFirst();
            deltas.add(deltaNs);

            /* floor (<15 ms) */
            boolean floorHit = deltaNs < FLOOR_NS;
            if (floorTrack.size() == FLOOR_WINDOW) floorTrack.removeFirst();
            floorTrack.add(floorHit);
            if (countTrue(floorTrack) >= FLOOR_HITS_MAX &&
                flagAndAlert((isPlacement ? "PLACEMENT" : "USE") + " <15 ms floor breach") &&
                shouldModifyPackets()) {
                event.setCancelled(true);
                player.onPacketCancel();
            }

            /* fast-streak ≤95 ms */
            if (deltaNs <= FAST_STREAK_NS) {
                if (fastStart == -1L) { fastStart = now; fastCount = 1; }
                else fastCount++;
            } else { fastStart = -1L; fastCount = 0; }

            if (debug)
                player.sendMessage((isPlacement ? "[P] " : "[U] ") +
                                   "Δ=" + deltaNs / 1_000_000D + " ms");
        }

        updateState(isPlacement, now, fastStart, fastCount);

        /* ---------- window evaluation ---------- */
        if (deltas.size() == WINDOW) {
            double avgNs = average(deltas);
            double stdNs = Math.max(MIN_STD_NS, standardDeviation(deltas, avgNs));
            double cov   = stdNs / avgNs;

            /* CoV-stability */
            if (covSeries.size() == WINDOW) covSeries.removeFirst();
            covSeries.add(cov);
            double covSigma = Double.POSITIVE_INFINITY;
            if (covSeries.size() == WINDOW) {
                double mu = average(covSeries);
                covSigma  = standardDeviation(covSeries, mu);
            }
            double covSigmaLimit = covVarLimit(avgNs);
            boolean covStable = (covSeries.size() == WINDOW) && (covSigma < covSigmaLimit);

            /* σ-stability on raw deltas */
            if (Math.abs(stdNs - (avgNs * SIGMA_STABLE_BAND)) <= avgNs * SIGMA_STABLE_BAND)
                sigmaStable++;
            else sigmaStable = 0;
            if (sigmaStable >= SIGMA_STABLE_TARGET &&
                flagAndAlert((isPlacement ? "PLACEMENT" : "USE") + " σ stable ±3 % for 150 packets") &&
                shouldModifyPackets()) {
                event.setCancelled(true);
                player.onPacketCancel();
            }
            if (isPlacement) placeSigmaStable = sigmaStable;
            else              useSigmaStable  = sigmaStable;

            /* -------- exhaustion: streak average -------- */
            boolean exhaustionAutoFlag = false;
            double streakAvgNs = Double.MAX_VALUE;
            if (fastStart != -1L && fastCount > 0) {
                streakAvgNs = (now - fastStart) / (double) fastCount;
                if (streakAvgNs <= CPS19_NS) {           // ≥19 CPS on average
                    /* same time-to-flag curve, fed by streak average speed */
                    double xMs = streakAvgNs / 1_000_000.0;
                    double tfFlag;
                    if (xMs <= 55.0)      tfFlag = 5.0;
                    else if (xMs <= 75.0) tfFlag = 0.10 * xMs - 0.5;
                    else if (xMs <= 95.0) tfFlag = 7.0 + (xMs - 75.0) * 0.5;
                    else                  tfFlag = Double.MAX_VALUE;
                    exhaustionAutoFlag = (now - fastStart) / 1_000_000_000.0 >= tfFlag;
                }
            }

            if (debug)
                player.sendMessage((isPlacement ? "[P] " : "[U] ") +
                                   String.format("AVG=%.2f ms σ=%.2f ms cov=%.3f σ(cov)=%.3f<%.3f streak-μ=%.2f ms",
                                                 avgNs / 1_000_000D, stdNs / 1_000_000D,
                                                 cov, covSigma, covSigmaLimit,
                                                 streakAvgNs / 1_000_000D));

            /* --- primary decision path --- */
            if (avgNs <= MAX_FLAG_AVG_NS || exhaustionAutoFlag) {
                double baseLimit = covBaseLimit(avgNs);

                /* exhaustion widens limit */
                double effLimit = baseLimit;
                if (fastStart != -1L) {
                    long runNs = now - fastStart;
                    if (runNs > EXH_START_NS) {
                        double t = Math.min(1D,
                                (double) (runNs - EXH_START_NS) /
                                (double) (EXH_FULL_NS - EXH_START_NS));
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
                                "%s μ=%.2f ms σ=%.2f ms cov=%.3f lim=%.3f σ(cov)=%.3f<%.3f EXH=%s",
                                tag, avgNs / 1_000_000D, stdNs / 1_000_000D,
                                cov, effLimit, covSigma, covSigmaLimit, exhaustionAutoFlag))
                                && shouldModifyPackets()) {
                            event.setCancelled(true);
                            player.onPacketCancel();
                        }
                        buf = 0;
                    }
                } else buf = Math.max(0, buf - 1);

                if (isPlacement) placeBuf = buf;
                else              useBuf   = buf;
            }
        }
    }

    /* ---------------- tiny utils ---------------- */
    private void updateState(boolean placement, long now, long fs, long fc) {
        if (placement) {
            lastPlaceTime  = now;
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

    /* Δ-domain CoV limit – 0.50 @1 ms → 0.35 @60 ms → 0.15 @150 ms */
    private static double covBaseLimit(double avgNs) {
        if (avgNs <= P1_NS) {
            double ratio = (P1_NS - Math.max(avgNs, MIN_COV_NS)) / (double) (P1_NS - MIN_COV_NS);
            return 0.35D + ratio * 0.15D;
        }
        if (avgNs <= MAX_FLAG_AVG_NS) {
            double ratio = (avgNs - P1_NS) / (double) (MAX_FLAG_AVG_NS - P1_NS);
            return 0.35D - ratio * 0.20D;
        }
        return 0.15D;
    }

    /* σ(Cov) limit – 0.50 @1 ms → 0.10 @60 ms → 0.05 @150 ms */
    private static double covVarLimit(double avgNs) {
        if (avgNs >= P1_NS) {
            if (avgNs >= MAX_FLAG_AVG_NS) return 0.05D;
            double ratio = (avgNs - P1_NS) / (double) (MAX_FLAG_AVG_NS - P1_NS);
            return 0.10D - ratio * 0.05D;
        }
        double ratio = (P1_NS - Math.max(avgNs, MIN_COV_NS)) / (double) (P1_NS - MIN_COV_NS);
        return 0.10D + ratio * 0.40D;
    }
}