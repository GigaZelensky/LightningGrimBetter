package ac.grim.boar.anticheat.data.input;

import ac.grim.boar.anticheat.prediction.engine.data.Vector;
import ac.grim.boar.anticheat.util.math.Vec3;

public record PredictionData(Vector vector, Vec3 before, Vec3 after, Vec3 tickEnd) {
}
