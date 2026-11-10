package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.player.data.StaticSeatTracker;

// epoch is StaticSeatTracker's sent epoch: the actor's epoch, or the new epoch for RESET. Acks from an older epoch are dropped.
public record StaticSeatAck(StaticSeatTracker.Actor actor, Kind kind, long epoch) implements Acknowledgment {
    public enum Kind { SPAWN, LINK, UNLINK, REMOVE, MOVE, ROTATE, RESET }

    public StaticSeatAck(StaticSeatTracker.Actor actor, Kind kind) {
        this(actor, kind, actor.epoch);
    }
}
