
package ac.grim.grimac.checks.impl.prediction;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;

// Boar embedded imports
import ac.grim.boar.anticheat.Boar;
import ac.grim.boar.anticheat.player.BoarPlayer;
import ac.grim.boar.anticheat.prediction.engine.base.PredictionEngine;

@CheckData(name = "BoarPrediction")
public class BoarPrediction extends Check implements PostPredictionCheck {

    private final BoarPlayer boarPlayer;
    private final PredictionEngine engine;

    public BoarPrediction(GrimPlayer playerData) {
        super(playerData);
        // Minimal glue: instantiate Boar engine for this player
        // For now, we default to GroundAndAir engine; real logic can select per state.
        this.boarPlayer = new BoarPlayer(playerData.getUuid(), playerData.getUsername());
        this.engine = Boar.getEngineFactory().groundAndAir(); // placeholder; adjust if factory differs
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        // Feed the player's predicted position into Boar (very rough stub)
        try {
            double x = predictionComplete.getTargetX();
            double y = predictionComplete.getTargetY();
            double z = predictionComplete.getTargetZ();
            engine.applyMovement(boarPlayer, x, y, z);

            double offset = engine.getOffset(boarPlayer);
            if (offset > 0.31) { // TODO make configurable
                flag(String.format("offset=%.3f", offset));
            }
        } catch (Throwable t) {
            // fail-safe: never crash server
        }
    }
}
