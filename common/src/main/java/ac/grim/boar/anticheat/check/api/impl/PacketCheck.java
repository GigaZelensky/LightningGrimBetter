package ac.grim.boar.anticheat.check.api.impl;

import ac.grim.boar.anticheat.check.api.Check;
import ac.grim.boar.anticheat.player.BoarPlayer;
import ac.grim.boar.protocol.listener.PacketListener;

public class PacketCheck extends Check implements PacketListener {
    public PacketCheck(BoarPlayer player) {
        super(player);
    }
}
