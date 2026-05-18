package ac.boar.anticheat.teleport;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.data.input.PredictionData;
import ac.boar.anticheat.data.input.TickData;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.teleport.data.RewindData;
import ac.boar.anticheat.teleport.data.RewindHistory;
import ac.boar.anticheat.teleport.data.TeleportData;
import ac.boar.anticheat.util.math.Vec3;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.protocol.bedrock.data.PredictionType;
import org.cloudburstmc.protocol.bedrock.packet.CorrectPlayerMovePredictionPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;

import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;

@RequiredArgsConstructor
public class TeleportUtil {
    private final BoarPlayer player;

    @Getter
    private Vec3 lastKnowValid = Vec3.ZERO;

    @Getter
    private final Queue<TeleportData> queuedTeleports = new ConcurrentLinkedQueue<>();

    public void teleport(final Vec3 vec3) {
        if (this.isTeleporting()) {
            return;
        }

        final MovePlayerPacket packet = new MovePlayerPacket();
        packet.setRuntimeEntityId(player.runtimeEntityId);
        packet.setPosition(vec3.toVector3f());
        packet.setRotation(player.rotation);
        packet.setOnGround(false);
        packet.setMode(MovePlayerPacket.Mode.TELEPORT);
        packet.setTeleportationCause(MovePlayerPacket.TeleportationCause.BEHAVIOR);

        this.player.getBedrockSession().sendPacket(packet);
    }

    public void queue(TeleportData data) {
        this.queuedTeleports.add(data);
        player.sendLatencyStack(data::accept);
    }

    public boolean isTeleporting() {
        return !this.queuedTeleports.isEmpty();
    }

    public boolean isHardTeleporting() {
        return !this.queuedTeleports.stream().filter(teleport -> !(teleport instanceof RewindData)).toList().isEmpty();
    }

    // Rewind
    @Getter
    private final Map<Long, TickData> authInputHistory = new ConcurrentSkipListMap<>();
    private final Map<Long, RewindHistory> rewindHistory = new ConcurrentSkipListMap<>();

    public void rewind(long tick) {
        this.rewind(this.rewindHistory.getOrDefault(tick, new RewindHistory(player.tick, this.lastKnowValid, player.predictionResult)));
    }

    public void rewind(final RewindHistory rewind) {
        if (this.isTeleporting()) {
            return;
        }

        final PredictionData data = rewind.data();

        final boolean onGround = data.before().y != data.after().y && data.before().y < 0;

        final long tick = rewind.tick();
        final CorrectPlayerMovePredictionPacket packet = new CorrectPlayerMovePredictionPacket();
        packet.setPosition(rewind.position().add(data.after()).toVector3f());
        packet.setOnGround(onGround);
        packet.setTick(tick);
        packet.setDelta(data.tickEnd().toVector3f());
        packet.setVehicleRotation(Vector2f.ZERO);
        packet.setPredictionType(player.vehicleData != null ? PredictionType.VEHICLE : PredictionType.PLAYER);

        this.player.getBedrockSession().sendPacket(packet);
    }

    public void cacheRewindHistory(long tick, Vec3 position) {
        this.rewindHistory.put(tick, new RewindHistory(tick, this.lastKnowValid.clone(), player.predictionResult));
        this.lastKnowValid = position;
    }

    public void pollRewindHistory() {
        final Iterator<Map.Entry<Long, RewindHistory>> iterator = this.rewindHistory.entrySet().iterator();
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
