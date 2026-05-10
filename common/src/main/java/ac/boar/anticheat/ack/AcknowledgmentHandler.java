package ac.boar.anticheat.ack;

import ac.boar.anticheat.player.BoarPlayer;

@FunctionalInterface
public interface AcknowledgmentHandler<A extends Acknowledgment> {

    void handle(BoarPlayer player, A ack);
}
