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
 * Design
 * • Independent timing windows for PLACEMENT / USE.  
 * • Dynamic CoV curve:  
 *   ─ 0.40 @ 50 ms → 0.25 @ 60 ms → 0.15 @ 150 ms.  
 * • Physiological floor (<15 ms) flags impossible speeds.  
 * • Exhaustion auto-flag – linear 5 s (≤55 ms) to 7 s (75 ms), disabled ≥95 ms.  
 * • σ-stability: if σ stays within ±3 % of μ for 150 packets, flag.  
 * • Six-tick buffer reduces burst false-positives.  
 * • Debug stream gated by “grim.debug.fastplace”.
 */
@CheckData(name = "FastPlace", experimental = true)
public class FastPlace extends Check implements PacketCheck {

    private static final int  WINDOW          = 15;
    private static final long MAX_GAP_NS      = 200_000_000L;     // 200 ms
    private static final long MAX_FLAG_AVG_NS = 150_000_000L;     // 150 ms
    private static final long P0_NS           = 50_000_000L;      //  50 ms
    private static final long P1_NS           = 60_000_000L;      //  60 ms
    private static final long MIN_STD_NS      = 4_000_000L;       //   4 ms

    private static final long  FLOOR_NS       = 15_000_000L;      // 15 ms
    private static final int   FLOOR_WINDOW   = 6;
    private static final int   FLOOR_HITS_MAX = 4;

    private static final long   FAST_THRESHOLD_NS = 59_000_000L;  // ≥17 cps
    private static final long   EXH_START_NS      = 1_000_000_000L;  // 1 s
    private static final long   EXH_FULL_NS       = 12_000_000_000L; // 12 s
    private static final long   CPS19_NS          = 53_000_000L;     // ≈19 cps
    private static final double MAX_DEVIATION_LIM = 0.80D;

    private static final int BUFFER_MAX = 6;
    private static final int SIGMA_STABLE_TARGET = 150;
    private static final double SIGMA_STABLE_BAND = 0.03D;          // ±3 %

    private final Deque<Long>    placeDeltas = new ArrayDeque<>(WINDOW);
    private final Deque<Long>     useDeltas  = new ArrayDeque<>(WINDOW);
    private final Deque<Boolean> placeFloor  = new ArrayDeque<>(FLOOR_WINDOW);
    private final Deque<Boolean>  useFloor   = new ArrayDeque<>(FLOOR_WINDOW);

    private long lastPlaceTime = -1L, placeFastStart = -1L;
    private long  lastUseTime  = -1L,  useFastStart  = -1L;
    private int   placeBuf     = 0,    useBuf        = 0;
    private int   placeSigmaStable = 0, useSigmaStable = 0;

    public FastPlace(@NotNull GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        long now = System.nanoTime();

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            handleStream(now, placeDeltas, placeFloor, true,  event);
        } else if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            handleStream(now, useDeltas,  useFloor,  false, event);
        }
    }

    private void handleStream(long now,
                              Deque<Long> deltas,
                              Deque<Boolean> floorTrack,
                              boolean isPlacement,
                              PacketReceiveEvent event) {

        boolean debug = player.hasPermission("grim.debug.fastplace");

        long lastTime  = isPlacement ? lastPlaceTime  : lastUseTime;
        long fastStart = isPlacement ? placeFastStart : useFastStart;
        int  sigmaStable = isPlacement ? placeSigmaStable : useSigmaStable;

        /* ---------- delta intake ---------- */
        if (lastTime != -1L) {
            long deltaNs = now - lastTime;
            if (deltaNs <= 0L) return;
            if (deltaNs > MAX_GAP_NS) {
                deltas.clear(); floorTrack.clear();
                if (isPlacement) { lastPlaceTime = now; placeFastStart = -1L; }
                else             {  lastUseTime  = now;  useFastStart  = -1L; }
                return;
            }

            if (deltas.size() == WINDOW) deltas.removeFirst();
            deltas.add(deltaNs);

            /* floor check */
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

            /* exhaustion start tracking */
            if (deltaNs <= FAST_THRESHOLD_NS) {
                if (fastStart == -1L) fastStart = now;
            } else fastStart = -1L;

            if (debug) player.sendMessage((isPlacement ? "[P] " : "[U] ") +
                    "Δ=" + (deltaNs / 1_000_000D) + "ms");
        }

        if (isPlacement) { lastPlaceTime = now; placeFastStart = fastStart; }
        else             {  lastUseTime  = now;  useFastStart  = fastStart; }

        /* ---------- window evaluation ---------- */
        if (deltas.size() == WINDOW) {
            double avgNs = average(deltas);
            double stdNs = Math.max(MIN_STD_NS, standardDeviation(deltas, avgNs));
            double cov   = stdNs / avgNs;

            /* σ-stability tracker */
            if (Math.abs(stdNs - (avgNs * SIGMA_STABLE_BAND)) <= avgNs * SIGMA_STABLE_BAND)
                sigmaStable++;
            else sigmaStable = 0;

            if (sigmaStable >= SIGMA_STABLE_TARGET) {
                String tag = isPlacement ? "PLACEMENT" : "USE";
                if (flagAndAlert(tag + " σ stable ±3 % for 150 packets") && shouldModifyPackets()) {
                    event.setCancelled(true); player.onPacketCancel();
                }
                sigmaStable = 0;
            }

            if (isPlacement) placeSigmaStable = sigmaStable; else useSigmaStable = sigmaStable;

            /* dynamic exhaustion */
            double xMs = avgNs / 1_000_000.0;
            double tfFlag;
            if (xMs <= 55.0) tfFlag = 5.0;
            else if (xMs <= 75.0) tfFlag = 0.10 * xMs - 0.5; // 55→5 s, 75→7 s
            else if (xMs <= 95.0) tfFlag = 7.0 + (xMs - 75.0) * 0.5; // slow stretch
            else tfFlag = Double.MAX_VALUE;

            boolean exhaustionAutoFlag = false;
            if (fastStart != -1L && avgNs <= CPS19_NS) {
                double runSec = (now - fastStart) / 1_000_000_000.0;
                exhaustionAutoFlag = runSec >= tfFlag;
            }

            if (debug) player.sendMessage((isPlacement ? "[P] " : "[U] ") +
                    String.format("AVG=%.2fms, σ=%.2fms, cov=%.3f",
                            avgNs / 1_000_000D, stdNs / 1_000_000D, cov));

            if (avgNs <= MAX_FLAG_AVG_NS || exhaustionAutoFlag) {

                /* CoV curve */
                double baseLimit;
                if (avgNs <= P0_NS) {
                    baseLimit = 0.40D;
                } else if (avgNs <= P1_NS) {
                    baseLimit = 0.45D - 0.15D *
                            ((avgNs - P0_NS) / (double) (P1_NS - P0_NS));
                } else {
                    baseLimit = 0.25D - 0.10D *
                            ((avgNs - P1_NS) / (double) (MAX_FLAG_AVG_NS - P1_NS));
                }
                baseLimit = Math.max(baseLimit, 0.15D);

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

                boolean breach = cov < effLimit || exhaustionAutoFlag;
                int buf = isPlacement ? placeBuf : useBuf;

                if (breach) {
                    buf = Math.min(BUFFER_MAX, buf + 1);
                    if (buf >= BUFFER_MAX) {
                        String tag = isPlacement ? "PLACEMENT" : "USE";
                        if (flagAndAlert(String.format(
                                "%s μ=%.2fms σ=%.2fms cov=%.3f limit=%.3f EXH=%s",
                                tag, avgNs / 1_000_000D, stdNs / 1_000_000D,
                                cov, effLimit, exhaustionAutoFlag))
                                && shouldModifyPackets()) {
                            event.setCancelled(true); player.onPacketCancel();
                        }
                        buf = 0;
                    }
                } else buf = Math.max(0, buf - 1);

                if (isPlacement) placeBuf = buf; else useBuf = buf;
            }
        }
    }

    /* ---------------- helpers ---------------- */

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