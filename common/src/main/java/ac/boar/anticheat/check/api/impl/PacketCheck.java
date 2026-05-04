package ac.boar.anticheat.check.api.impl;

import ac.boar.anticheat.check.api.Check;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.protocol.api.PacketListener;

public class PacketCheck extends Check implements PacketListener {
    public PacketCheck(BoarPlayer player) {
        super(player);
    }
}
