package ac.grim.boar.anticheat.check.impl.velocity;

import ac.grim.boar.anticheat.check.api.Check;
import ac.grim.boar.anticheat.check.api.annotations.CheckInfo;
import ac.grim.boar.anticheat.player.BoarPlayer;

@CheckInfo(name = "Velocity", type = "*")
public class Velocity extends Check {
    public Velocity(BoarPlayer player) {
        super(player);
    }
}
