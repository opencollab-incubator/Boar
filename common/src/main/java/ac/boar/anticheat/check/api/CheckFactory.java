package ac.boar.anticheat.check.api;

import ac.boar.anticheat.player.BoarPlayer;

@FunctionalInterface
public interface CheckFactory {

    Check create(BoarPlayer player);
}
