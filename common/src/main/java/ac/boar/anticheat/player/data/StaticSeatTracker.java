package ac.boar.anticheat.player.data;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.types.StaticSeatAck;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.EntityMovementFlags;
import ac.boar.anticheat.util.MathUtil;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityLinkData;
import org.cloudburstmc.protocol.bedrock.packet.*;

import java.util.HashMap;
import java.util.Map;

/** Tracks static seats the server owns, apart from normal entities. These actors have no EntityDefinition. */
public final class StaticSeatTracker {
    // Seats as the server sent them, by unique ID and runtime ID. Server packets (spawn, link, remove, movement) look up their IDs here.
    private final Map<Long, Actor> sentUnique = new HashMap<>(), sentRuntime = new HashMap<>();
    // Seats the client has acknowledged (SPAWN ack). The ack handler (acknowledge) checks against these.
    private final Map<Long, Actor> visibleUnique = new HashMap<>(), visibleRuntime = new HashMap<>();
    // sentEpoch goes up on each resetSent (dimension change or respawn). Each StaticSeatAck carries its actor's epoch (RESET carries the new one).
    // visibleEpoch is the newest epoch acknowledged: older acks are dropped, and a newer one clears the visible maps and the seat.
    // serial goes up for each new seat, so a SPAWN ack for an older seat never replaces a newer seat with the same ID.
    private long sentEpoch, visibleEpoch, serial;

    public static final class Actor {
        public final long uniqueId, runtimeId, epoch;
        public final Vector3f origin;
        public final float yaw;
        public boolean rotationChanged;
        private final long serial;
        private boolean linked, moved;

        private Actor(long uniqueId, long runtimeId, Vector3f origin, float yaw, long epoch, long serial) {
            this.uniqueId = uniqueId;
            this.runtimeId = runtimeId;
            this.origin = origin;
            this.yaw = yaw;
            this.epoch = epoch;
            this.serial = serial;
        }
    }

    public synchronized void spawn(BoarPlayer player, AddEntityPacket packet) {
        this.retire(player, packet.getRuntimeEntityId(), packet.getUniqueEntityId());
        if (player.getEntityAccessor().definitionByRuntimeId(packet.getRuntimeEntityId()) != null
                || packet.getRuntimeEntityId() == player.runtimeEntityId
                || !Boar.getConfig().staticSeatIdentifiers().contains(packet.getIdentifier())
                || !MathUtil.finite(packet.getPosition())
                || !zero(packet.getMotion())
                || packet.getRotation() == null
                || !Float.isFinite(packet.getRotation().getY())) {
            return;
        }
        final Actor actor = new Actor(packet.getUniqueEntityId(), packet.getRuntimeEntityId(), packet.getPosition(), packet.getRotation().getY(),
                this.sentEpoch, ++this.serial);
        this.sentUnique.put(actor.uniqueId, actor);
        this.sentRuntime.put(actor.runtimeId, actor);
        player.sendLatencyStack(new StaticSeatAck(actor, StaticSeatAck.Kind.SPAWN));
        for (EntityLinkData link : packet.getEntityLinks()) {
            link(player, link);
        }
    }

    /**
     * Queues a LINK or UNLINK ack if this link is from a sent seat to this player. Returns true if it did.
     * Input prediction never uses the sent maps.
     */
    public synchronized boolean link(BoarPlayer player, EntityLinkData link) {
        final Actor actor = this.sentUnique.get(link.getFrom());
        if (actor == null || link.getTo() != player.uniqueEntityId) {
            return false;
        }
        final StaticSeatAck.Kind kind = link.getType() == EntityLinkData.Type.REMOVE ? StaticSeatAck.Kind.UNLINK : StaticSeatAck.Kind.LINK;
        player.queueAcknowledgment(new StaticSeatAck(actor, kind));
        return true;
    }

    public synchronized void remove(BoarPlayer player, long uniqueId) {
        retireSent(player, this.sentUnique.get(uniqueId));
    }

    public synchronized void retire(BoarPlayer player, long runtimeId, long uniqueId) {
        retireSent(player, this.sentRuntime.get(runtimeId));
        retireSent(player, this.sentUnique.get(uniqueId));
    }

    private void retireSent(BoarPlayer player, Actor actor) {
        if (actor == null) {
            return;
        }
        this.sentUnique.remove(actor.uniqueId, actor);
        this.sentRuntime.remove(actor.runtimeId, actor);
        player.queueAcknowledgment(new StaticSeatAck(actor, StaticSeatAck.Kind.REMOVE));
    }

    public synchronized void movement(BoarPlayer player, BedrockPacket packet) {
        final long runtimeId;
        switch (packet) {
            case MoveEntityDeltaPacket move -> runtimeId = move.getRuntimeEntityId();
            case MoveEntityAbsolutePacket move -> runtimeId = move.getRuntimeEntityId();
            case SetEntityMotionPacket motion -> runtimeId = motion.getRuntimeEntityId();
            case MovePlayerPacket move -> runtimeId = move.getRuntimeEntityId();
            default -> {
                return;
            }
        }
        final Actor actor = this.sentRuntime.get(runtimeId);
        if (actor == null) {
            return;
        }
        boolean changed;
        Float yaw = null;
        switch (packet) {
            case MoveEntityDeltaPacket move -> {
                if (move.getFlags().contains(MoveEntityDeltaPacket.Flag.HAS_YAW)) {
                    yaw = EntityMovementFlags.wireByteAngle(move.getYaw());
                }
                changed = move.getFlags().contains(MoveEntityDeltaPacket.Flag.HAS_X) && move.getX() != actor.origin.getX()
                        || move.getFlags().contains(MoveEntityDeltaPacket.Flag.HAS_Y) && move.getY() != actor.origin.getY()
                        || move.getFlags().contains(MoveEntityDeltaPacket.Flag.HAS_Z) && move.getZ() != actor.origin.getZ();
            }
            case MoveEntityAbsolutePacket move -> {
                yaw = EntityMovementFlags.wireByteAngle(move.getRotation().getY());
                changed = !actor.origin.equals(move.getPosition());
            }
            case SetEntityMotionPacket motion -> changed = !zero(motion.getMotion());
            case MovePlayerPacket move -> {
                if (move.getMode() != MovePlayerPacket.Mode.HEAD_ROTATION) {
                    yaw = move.getRotation().getY();
                }
                changed = move.getMode() != MovePlayerPacket.Mode.HEAD_ROTATION && !actor.origin.equals(move.getPosition());
            }
            default -> {
                return;
            }
        }
        if (changed) {
            player.queueAcknowledgment(new StaticSeatAck(actor, StaticSeatAck.Kind.MOVE));
        } else if (yaw != null && yaw != actor.yaw) {
            player.queueAcknowledgment(new StaticSeatAck(actor, StaticSeatAck.Kind.ROTATE));
        }
    }

    public synchronized void resetSent(BoarPlayer player) {
        this.sentUnique.clear();
        this.sentRuntime.clear();
        player.queueAcknowledgment(new StaticSeatAck(null, StaticSeatAck.Kind.RESET, ++this.sentEpoch));
    }

    public synchronized void acknowledge(BoarPlayer player, StaticSeatAck ack) {
        if (ack.epoch() < this.visibleEpoch) {
            return;
        }
        if (ack.epoch() > this.visibleEpoch) {
            this.visibleEpoch = ack.epoch();
            this.visibleUnique.clear();
            this.visibleRuntime.clear();
            player.staticSeat = null;
        }
        if (ack.kind() == StaticSeatAck.Kind.RESET) {
            return;
        }
        final Actor actor = ack.actor();
        if (ack.kind() == StaticSeatAck.Kind.SPAWN) {
            final Actor byUnique = this.visibleUnique.get(actor.uniqueId), byRuntime = this.visibleRuntime.get(actor.runtimeId);
            if (byUnique != null && byUnique.serial > actor.serial || byRuntime != null && byRuntime.serial > actor.serial) {
                return;
            }
            retireVisible(player, byUnique);
            retireVisible(player, byRuntime);
            this.visibleUnique.put(actor.uniqueId, actor);
            this.visibleRuntime.put(actor.runtimeId, actor);
            return;
        }
        if (this.visibleUnique.get(actor.uniqueId) != actor || this.visibleRuntime.get(actor.runtimeId) != actor) {
            return;
        }
        switch (ack.kind()) {
            case LINK -> {
                if (actor.linked || actor.moved || actor.rotationChanged && hasHorizontalSeatOffset(player)) {
                    return;
                }
                actor.linked = true;
                if (player.vehicleData != null) {
                    player.horseJumpInput.invalidate();
                }
                player.vehicleData = null;
                player.staticSeat = new StaticSeatData(actor, player.onGround);
            }
            case UNLINK -> {
                actor.linked = false;
                if (player.staticSeat != null && player.staticSeat.actor == actor) {
                    player.staticSeat.serverCleared = true;
                }
            }
            case REMOVE -> retireVisible(player, actor);
            case MOVE -> {
                actor.moved = true;
                if (player.staticSeat != null && player.staticSeat.actor == actor) {
                    player.staticSeat = null;
                }
            }
            case ROTATE -> {
                // Smooth turning (angular interpolation) is not part of the static-seat model. A vertical-only offset looks the same at any rotation.
                actor.rotationChanged = true;
                if (player.staticSeat != null && player.staticSeat.actor == actor && hasHorizontalSeatOffset(player)) {
                    player.staticSeat = null;
                }
            }
            default -> {}
        }
    }

    private void retireVisible(BoarPlayer player, Actor actor) {
        if (actor == null) {
            return;
        }
        this.visibleUnique.remove(actor.uniqueId, actor);
        this.visibleRuntime.remove(actor.runtimeId, actor);
        if (player.staticSeat != null && player.staticSeat.actor == actor) {
            player.staticSeat.serverCleared = true;
        }
    }

    private static boolean zero(Vector3f vector) {
        return vector == null || vector.getX() == 0 && vector.getY() == 0 && vector.getZ() == 0;
    }

    static boolean hasHorizontalSeatOffset(BoarPlayer player) {
        return player.seatOffset.getX() != 0 || player.seatOffset.getZ() != 0;
    }
}
