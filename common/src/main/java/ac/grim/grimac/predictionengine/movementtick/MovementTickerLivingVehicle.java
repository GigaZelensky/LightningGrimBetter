package ac.grim.grimac.predictionengine.movementtick;

import ac.grim.grimac.api.packet.protocol.PacketClientVersion;
import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.predictionengine.predictions.rideable.PredictionEngineRideableLava;
import ac.grim.grimac.predictionengine.predictions.rideable.PredictionEngineRideableNormal;
import ac.grim.grimac.predictionengine.predictions.rideable.PredictionEngineRideableWater;
import ac.grim.grimac.predictionengine.predictions.rideable.PredictionEngineRideableWaterLegacy;
import ac.grim.grimac.api.math.Vector3dm;
import ac.grim.grimac.utils.nmsutil.BlockProperties;

public class MovementTickerLivingVehicle extends MovementTicker {
    Vector3dm movementInput = new Vector3dm();

    public MovementTickerLivingVehicle(GrimPlayer player) {
        super(player);
    }

    @Override
    public void doWaterMove(float swimSpeed, boolean isFalling, float swimFriction) {
        if (player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_13)) {
            new PredictionEngineRideableWater(movementInput).guessBestMovement(swimSpeed, player, isFalling, player.gravity, swimFriction, player.lastY);
        } else {
            new PredictionEngineRideableWaterLegacy(movementInput).guessBestMovement(swimSpeed, player, player.gravity, swimFriction, player.lastY);
        }
    }

    @Override
    public void doLavaMove() {
        new PredictionEngineRideableLava(movementInput).guessBestMovement(0.02F, player);
    }

    @Override
    public void doNormalMove(float blockFriction) {
        new PredictionEngineRideableNormal(movementInput).guessBestMovement(BlockProperties.getFrictionInfluencedSpeed(blockFriction, player), player);
    }
}
