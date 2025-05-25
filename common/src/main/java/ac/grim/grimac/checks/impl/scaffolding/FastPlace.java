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

/**
 * FastPlace – production build (combined-window fork)
 *
 * Behaviour
 * ─────────
 * • **Single** timing window – begins at 5 packets and grows to 12.
 * • Δ-domain CoV limit: 0.50 @ 1 ms → 0.35 @ 60 ms → 0.15 @ 150 ms.
 * • σ(Cov) "cov-stability" starts after 12 samples:
 *     0.50 @ 1 ms → 0.050 @ 35 ms → 0.025 @ 65 ms → 0.005 @ 150 ms (quadratic).
 * • Floor protection: window-μ <35 ms (but ≥1 ms) for 7 of 8 consecutive
 *   windows → instant flag (combined).
 * • Exhaustion: ≥12 CPS widens limits after ≤23.5 s (CPS-dependent),
 *   ramping to max over +5 s (resets if 7/8 packets are slow).
 * • **Dynamic buffer** – the stricter the CoV/σ(Cov), the faster it fills.
 * • σ-stability on raw deltas (±3 %) flags after 150 packets.
 * • Six-tick buffer before any cancellation.
 * • Debug stream gated by "grim.debug.fastplace".
 */
@CheckData(name = "FastPlace", experimental = true)
public class FastPlace extends Check implements PacketCheck {

    /* window – grows from 5 to 12 */
    private static final int  WINDOW          = 12;             // max window
    private static final int  WINDOW_MIN      = 5;              // starting window

    private static final long MAX_GAP_NS      = 300_000_000L;   // 300 ms (min)
    private static final long MAX_FLAG_AVG_NS = 150_000_000L;   // 150 ms
    private static final long P1_NS           = 60_000_000L;    //  60 ms
    private static final long MIN_STD_NS      = 4_000_000L;     //   4 ms
    private static final long MIN_COV_NS      = 1_000_000L;     //   1 ms

    /* floor uses window-average */
    private static final long FLOOR_NS       = 35_000_000L;     // 35 ms
    private static final int  FLOOR_WINDOW   = 8;
    private static final int  FLOOR_HITS_MAX = 7;

    /* dynamic exhaustion */
    private static final long   THRESHOLD_NS_12CPS = 83_000_000L;  // ≈12 CPS
    private static final double EXH_CPS_MIN        = 12.0D;
    private static final double EXH_TIME_INTERCEPT = 23.5D;        // seconds
    private static final double EXH_TIME_SLOPE     = -0.875D;      // sec·CPS⁻¹
    private static final double EXH_TIME_MIN       = 6.0D;         // seconds
    private static final int    FAST_WINDOW_SIZE   = 8;
    private static final int    FAST_WINDOW_SLOW_RESET = 7;

    private static final int BUFFER_MAX = 6;
    private static final int SIGMA_STABLE_TARGET = 150;
    private static final double SIGMA_STABLE_BAND = 0.03D;      // ±3 %

    /* combined streams (PLACEMENT + USE) */
    private final Deque<Long>    combinedDeltas     = new ArrayDeque<>(WINDOW);
    private final Deque<Boolean> combinedFloor      = new ArrayDeque<>(FLOOR_WINDOW);
    private final Deque<Double>  combinedCovSeries  = new ArrayDeque<>(WINDOW);
    private final Deque<Boolean> combinedFastWindow = new ArrayDeque<>(FAST_WINDOW_SIZE);

    /* per-type fast window – still tracked for reset heuristics */
    private final Deque<Boolean> placeFastWindow = new ArrayDeque<>(FAST_WINDOW_SIZE);
    private final Deque<Boolean>   useFastWindow = new ArrayDeque<>(FAST_WINDOW_SIZE);

    private long combinedFastStart = -1L;
    private long combinedFastCount = 0L;

    private long lastPacketTime = -1L;
    private int  combinedBuf = 0;
    private int  combinedSigmaStable = 0;

    public FastPlace(@NotNull GrimPlayer player) { super(player); }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        long now = System.nanoTime();

        boolean placement = event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT;
        boolean use       = event.getPacketType() == PacketType.Play.Client.USE_ITEM;
        if (!placement && !use) return;

        Deque<Boolean> sideFast = placement ? placeFastWindow : useFastWindow;
        handle(now, sideFast, placement, event);
    }

    /* ---------------- core ---------------- */
    private void handle(long now,
                        Deque<Boolean> sideFastWindow,
                        boolean isPlacement,
                        PacketReceiveEvent event) {

        final boolean debug = player.hasPermission("grim.debug.fastplace");

        /* ---------- delta intake ---------- */
        if (lastPacketTime != -1L) {
            long deltaNs = now - lastPacketTime;
            if (deltaNs <= 0L) return;

            // dynamic gap: max(300 ms, μ * 6)
            double meanNs = combinedDeltas.isEmpty() ? deltaNs : average(combinedDeltas);
            long   gapNs  = Math.max(MAX_GAP_NS, (long)(meanNs * 6));

            if (deltaNs > gapNs) {
                if (debug) player.sendMessage(String.format("%s gap %.2f ms (reset)",
                        isPlacement ? "[P]" : "[U]", deltaNs / 1_000_000D));
                combinedDeltas.clear();
                combinedFloor.clear();
                combinedCovSeries.clear();
                combinedFastWindow.clear();
                placeFastWindow.clear();
                useFastWindow.clear();
                combinedFastStart = -1L;
                combinedFastCount = 0L;
            } else {
                if (combinedDeltas.size() == WINDOW) combinedDeltas.removeFirst();
                combinedDeltas.add(deltaNs);

                boolean isFast = deltaNs <= THRESHOLD_NS_12CPS;

                if (sideFastWindow.size() == FAST_WINDOW_SIZE) sideFastWindow.removeFirst();
                sideFastWindow.add(isFast);

                if (combinedFastWindow.size() == FAST_WINDOW_SIZE) combinedFastWindow.removeFirst();
                combinedFastWindow.add(isFast);

                if (isFast) {
                    if (combinedFastStart == -1L) { combinedFastStart = now; combinedFastCount = 1; }
                    else combinedFastCount++;
                }

                if (combinedFastWindow.size() == FAST_WINDOW_SIZE) {
                    int slow = FAST_WINDOW_SIZE - countTrue(combinedFastWindow);
                    if (slow >= FAST_WINDOW_SLOW_RESET) {
                        combinedFastStart = -1L;
                        combinedFastCount = 0L;
                        combinedFastWindow.clear();
                        placeFastWindow.clear();
                        useFastWindow.clear();
                    }
                }

                if (debug) player.sendMessage((isPlacement ? "[P] " : "[U] ") +
                        "Δ=" + deltaNs / 1_000_000D + " ms");
            }
        }

        lastPacketTime = now;

        /* ---------- window evaluation ---------- */
        if (combinedDeltas.size() < WINDOW_MIN) return;

        double avgNs = average(combinedDeltas);
        double stdNs = Math.max(MIN_STD_NS, standardDeviation(combinedDeltas, avgNs));
        double cov   = stdNs / avgNs;

        /* ---- item-in-hand inspection ---- */
        ItemStack inHand = player.getInventory().getItemInHand(InteractionHand.MAIN_HAND);
        boolean placeable = inHand != null && !inHand.isEmpty() &&
                            inHand.getType().getPlacedType() != null;

        /* bail out if the player isn’t holding a placeable block */
        if (!placeable) {
            combinedFloor.clear();
            return;
        }

        /* ---- floor by window-μ (<35 ms, but ≥1 ms) ---- */
        if (placeable) {
            boolean floorHit = avgNs >= MIN_COV_NS && avgNs < FLOOR_NS;
            if (combinedFloor.size() == FLOOR_WINDOW) combinedFloor.removeFirst();
            combinedFloor.add(floorHit);
            if (countTrue(combinedFloor) >= FLOOR_HITS_MAX &&
                flagAndAlert("window-μ <35 ms (4/6)") &&
                shouldModifyPackets()) {
                event.setCancelled(true);
                player.onPacketCancel();
            }
        } else combinedFloor.clear();

        /* ---- CoV-series for σ(Cov) ---- */
        if (combinedCovSeries.size() == WINDOW) combinedCovSeries.removeFirst();
        combinedCovSeries.add(cov);

        boolean covReady = combinedCovSeries.size() == WINDOW;
        double  covSigma = covReady ? standardDeviation(combinedCovSeries, average(combinedCovSeries))
                                    : Double.NaN;
        double  covLimit = covReady ? covVarLimit(avgNs) : Double.NaN;
        boolean covStable = covReady && covSigma < covLimit;

        /* ---- σ-stability on raw deltas (±3 %) ---- */
        if (Math.abs(stdNs - (avgNs * SIGMA_STABLE_BAND)) <= avgNs * SIGMA_STABLE_BAND)
            combinedSigmaStable++;
        else combinedSigmaStable = 0;
        if (combinedSigmaStable >= SIGMA_STABLE_TARGET &&
            flagAndAlert("σ stable ±3 % for 150 packets") &&
            shouldModifyPackets()) {
            event.setCancelled(true);
            player.onPacketCancel();
        }

        /* ---- exhaustion-driven limit widening ---- */
        double effLimit = covBaseLimit(avgNs);
        if (combinedFastStart != -1L && combinedFastCount > 0) {
            double streakAvgNs = (now - combinedFastStart) / (double) combinedFastCount;
            double streakCps   = 1_000_000_000.0D / streakAvgNs;

            if (streakCps >= EXH_CPS_MIN) {
                double tfFlag  = Math.max(EXH_TIME_MIN,
                                          EXH_TIME_INTERCEPT + EXH_TIME_SLOPE * streakCps);
                double elapsed = (now - combinedFastStart) / 1_000_000_000.0D;

                if (elapsed >= tfFlag) {
                    double ramp = Math.min(1.0D, (elapsed - tfFlag) / 5.0D); // 0 → 1 over 5 s
                    effLimit += (1.0D - effLimit) * ramp;                   // stretch to 1.0
                }
            }
        }

        if (avgNs <= MAX_FLAG_AVG_NS) {
            boolean breach = (cov < effLimit) || covStable;
            int buf = combinedBuf;

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
                    if (flagAndAlert(String.format(
                            "μ=%.2f ms σ=%.2f ms cov=%.3f lim=%.3f σ(cov)=%.3f<%.3f",
                            avgNs / 1_000_000D, stdNs / 1_000_000D,
                            cov, effLimit,
                            covReady ? covSigma : Double.NaN,
                            covReady ? covLimit : Double.NaN))
                            && shouldModifyPackets()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                    }
                    buf = 0;
                }
            } else buf = Math.max(0, buf - 1);

            combinedBuf = buf;
        }

        /* ---- verbose debug ---- */
        if (debug) player.sendMessage((isPlacement ? "[P] " : "[U] ") +
                String.format("AVG=%.2f ms σ=%.2f ms cov=%.3f σ(cov)=%s<%s",
                        avgNs / 1_000_000D, stdNs / 1_000_000D, cov,
                        covReady ? String.format("%.3f", covSigma) : "--",
                        covReady ? String.format("%.3f", covLimit) : "--"));
    }

    /* ---------------- helpers ---------------- */
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

    /* Δ-domain CoV limit – untouched */
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
        if (avgNs <= T0_NS) return 0.05D;     // 0 – 35 ms → 0.050

        if (avgNs <= T1_NS) {
            double t = (avgNs - T0_NS) / (double) (T1_NS - T0_NS);
            return 0.05D - 0.025D * t * t;     // 35 ms → 0.050 ▼ 65 ms → 0.025
        }

        if (avgNs <= T2_NS) {
            double t = (avgNs - T1_NS) / (double) (T2_NS - T1_NS);
            return 0.025D - 0.020D * t * t;    // 65 ms → 0.025 ▼ 150 ms → 0.005
        }

        return 0.005D;
    }
}