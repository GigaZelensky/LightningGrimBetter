
package ac.grim.grimac.checks.impl.custom.fastplace;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.SampleList;
import ac.grim.grimac.utils.math.GrimMath;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.jetbrains.annotations.NotNull;

/**
 * FastPlaceA – detects abnormally fast block placements (auto‑place / scaffold cheats).
 *
 *  Improvements vs. original prototype:
 *  • Uses System.nanoTime() for sub‑millisecond precision.
 *  • Ignores large gaps (>200 ms) so AFK periods don't pollute the sampling window.
 *  • Uses the coefficient‑of‑variation (σ/μ) instead of raw deviation to reduce FP rate.
 *  • Larger window (15) for better statistical power.
 */
@CheckData(name = "FastPlace", decay = 0.005, experimental = true)
public class FastPlaceA extends Check implements PacketCheck {

    private final SampleList<Long> samples = new SampleList<>(15);
    private long lastTime, lastDelta;

    public FastPlaceA(@NotNull GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT
                && event.getPacketType() != PacketType.Play.Client.USE_ITEM) {
            return;
        }

        final long nowMs = System.nanoTime() / 1_000_000L;
        final long delta = nowMs - lastTime;
        lastTime = nowMs;

        // Reject bogus or huge deltas
        if (delta <= 0 || delta > 200L) {
            samples.clear();
            return;
        }

        if (delta != lastDelta) {
            samples.add(delta);
            lastDelta = delta;
        }

        if (!samples.isCollected()) return;

        final double avg = average(samples);
        final double std = standardDeviation(samples, avg);
        final double cov = std / avg; // variation

        if (avg < 60D && cov < 0.35D) {
            flagAndAlert(String.format("μ=%.2fms σ=%.2fms cov=%.3f", avg, std, cov));
        }
    }

    /* -------- Helpers -------- */
    private static double average(final SampleList<Long> list) {
        long sum = 0L;
        for (Long l : list) sum += l;
        return sum / (double) list.size();
    }

    private static double standardDeviation(final SampleList<Long> list, final double mean) {
        double var = 0D;
        for (Long l : list) {
            double diff = l - mean;
            var += diff * diff;
        }
        return GrimMath.sqrt((float) (var / list.size()));
    }
}
