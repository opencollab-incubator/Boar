package ac.boar.anticheat.teleport;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.types.TeleportAcceptAck;
import ac.boar.anticheat.data.input.PredictionData;
import ac.boar.anticheat.data.input.TickData;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.teleport.data.TeleportCache;
import ac.boar.anticheat.teleport.data.rewind.RewindData;
import ac.boar.anticheat.util.math.Vec3;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.PredictionType;
import org.cloudburstmc.protocol.bedrock.packet.CorrectPlayerMovePredictionPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;

import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;

@RequiredArgsConstructor
@Getter
public class TeleportUtil {
    private final BoarPlayer player;

    private Vector3f lastKnownValid = Vector3f.ZERO;

    // Normal teleport part.
    private final Queue<TeleportCache> queuedTeleports = new ConcurrentLinkedQueue<>();

    public boolean isTeleporting() {
        return !this.queuedTeleports.isEmpty();
    }

    public void teleportTo(final Vector3f position) {
        this.teleportTo(new TeleportCache.Normal(new Vec3(position), false));
    }

    public void teleportTo(final TeleportCache cache) {
        if (this.isTeleporting()) {
            Boar.debug("[movement-debug] skipped teleport reason=already-teleporting queued=" + this.queuedTeleports.size(), Boar.DebugMessage.WARNING);
            return;
        }

        if (cache instanceof TeleportCache.Rewind) {
            throw new RuntimeException("You're not suppose to pass rewind teleport to this method!");
        }

        final TeleportCache.Normal teleport = (TeleportCache.Normal) cache;
        final MovePlayerPacket packet = new MovePlayerPacket();
        packet.setRuntimeEntityId(player.runtimeEntityId);
        packet.setPosition(teleport.getPosition().toVector3f());
        packet.setRotation(player.rotation);
        packet.setOnGround(teleport.isOnGround());
        packet.setMode(MovePlayerPacket.Mode.TELEPORT);
        packet.setTeleportationCause(MovePlayerPacket.TeleportationCause.BEHAVIOR);

        this.player.getConnection().sendPacket(packet);
        Boar.debug("[movement-debug] sent teleport pos=" + teleport.getPosition() + " lastKnown=" + this.lastKnownValid, Boar.DebugMessage.WARNING);
    }

    public void queueTeleport(final Vec3 position, final boolean onGround) {
        queue(new TeleportCache.Normal(position, onGround));
        this.lastKnownValid = position.toVector3f();
    }

    public void queue(TeleportCache cache) {
        this.queuedTeleports.add(cache);
        player.sendLatencyStack(new TeleportAcceptAck(cache));
    }

    // Rewind teleport part.
    private final Map<Long, TickData> authInputHistory = new ConcurrentSkipListMap<>();
    private final Map<Long, RewindData> rewindHistory = new ConcurrentSkipListMap<>();

    public void rewind(long tick) {
        this.rewind(this.rewindHistory.getOrDefault(tick, new RewindData(player.tick, this.lastKnownValid, player.predictionResult)));
    }

    public void rewind(final RewindData rewind) {
        if (this.isTeleporting()) {
            Boar.debug("[movement-debug] skipped rewind reason=already-teleporting queued=" + this.queuedTeleports.size() + " tick=" + rewind.tick(), Boar.DebugMessage.WARNING);
            return;
        }

        final PredictionData data = rewind.data();
        final boolean onGround = data.before().y != data.after().y && data.before().y < 0;
        final long tick = rewind.tick();
        final CorrectPlayerMovePredictionPacket packet = new CorrectPlayerMovePredictionPacket();
        packet.setPosition(rewind.position().add(data.after().toVector3f()));
        packet.setOnGround(onGround);
        packet.setTick(tick);
        packet.setDelta(data.tickEnd().toVector3f());
        packet.setVehicleRotation(Vector2f.ZERO);
        packet.setPredictionType(player.vehicleData != null ? PredictionType.VEHICLE : PredictionType.PLAYER);

        queue(new TeleportCache.Rewind(tick, new Vec3(packet.getPosition()), new Vec3(packet.getDelta()), onGround));
        this.player.getConnection().sendPacketImmediately(packet);
        Boar.debug("sent rewind tick=" + tick + " pos=" + packet.getPosition() + " delta=" + packet.getDelta() + " onGround=" + onGround, Boar.DebugMessage.WARNING);
    }

    public void cachePosition(long tick, Vector3f position) {
        this.rewindHistory.put(tick, new RewindData(tick, this.lastKnownValid.clone(), player.predictionResult));
        this.lastKnownValid = position;
    }

    public void pollRewindHistory() {
        final Iterator<Map.Entry<Long, RewindData>> iterator = this.rewindHistory.entrySet().iterator();
        while (iterator.hasNext() && this.rewindHistory.size() > Boar.getConfig().rewindHistory()) {
            iterator.next();
            iterator.remove();
        }

        final Iterator<Map.Entry<Long, TickData>> iterator1 = this.authInputHistory.entrySet().iterator();
        while (iterator1.hasNext() && this.authInputHistory.size() > Boar.getConfig().rewindHistory()) {
            iterator1.next();
            iterator1.remove();
        }
    }
}
