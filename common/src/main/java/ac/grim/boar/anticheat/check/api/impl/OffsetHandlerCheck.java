package ac.grim.boar.anticheat.check.api.impl;

import ac.grim.boar.anticheat.check.api.Check;
import ac.grim.boar.anticheat.player.BoarPlayer;

public class OffsetHandlerCheck extends Check {
    public OffsetHandlerCheck(BoarPlayer player) {
        super(player);
    }

    public void onPredictionComplete(double offset) {
    }
}
