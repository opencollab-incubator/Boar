package ac.boar.anticheat.compensated.world;

import org.cloudburstmc.math.vector.Vector3i;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Keeps track of two things so block resyncs don't fight with block updates a server sends:
// 1. Blocks the client placed that the server has not answered yet.
// 2. Server block updates that the client has not received yet.
// Server updates are counted on the outbound thread and removed on ack, so both maps are concurrent.
public final class BlockPlacementTracker {

    // How long we keep track of a player's own block placement if the server never answers.
    private static final int PLACEMENT_LIFETIME = 200;

    private final Map<Vector3i, Integer> unansweredPlacements = new ConcurrentHashMap<>();
    private final Map<Vector3i, Integer> pendingServerUpdates = new ConcurrentHashMap<>();

    public void onClientPlace(Vector3i position) {
        this.unansweredPlacements.put(key(position), PLACEMENT_LIFETIME);
    }

    public boolean isUnansweredPlacement(Vector3i position) {
        return this.unansweredPlacements.containsKey(key(position));
    }

    public void onServerUpdateSent(Vector3i position) {
        this.pendingServerUpdates.merge(key(position), 1, Integer::sum);
    }

    public void onServerUpdateReceived(Vector3i position, int layer) {
        final Vector3i key = key(position);
        this.pendingServerUpdates.computeIfPresent(key, (pos, count) -> count > 1 ? count - 1 : null);
        if (layer == 0) {
            this.unansweredPlacements.remove(key);
        }
    }

    public boolean hasPendingServerUpdate(Vector3i position) {
        return this.pendingServerUpdates.containsKey(key(position));
    }

    public void tick() {
        this.unansweredPlacements.replaceAll((pos, ticks) -> ticks - 1);
        this.unansweredPlacements.values().removeIf(ticks -> ticks <= 0);
    }

    // Server packets can give us their own Vector3i class, which doesn't match ours as a map key.
    // Copy every position into the same class so lookups always work.
    private static Vector3i key(Vector3i position) {
        return Vector3i.from(position.getX(), position.getY(), position.getZ());
    }

    public void clearPlacements() {
        this.unansweredPlacements.clear();
    }
}
