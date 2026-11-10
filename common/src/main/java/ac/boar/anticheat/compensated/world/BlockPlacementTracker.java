package ac.boar.anticheat.compensated.world;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.util.math.Direction;
import org.cloudburstmc.math.vector.Vector3i;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Tracks the player's block placements against the block updates the server sends:
// 1. Blocks the client placed that the server has not answered yet, so block resyncs don't overwrite them.
// 2. Server block updates the client has not received yet, so block resyncs don't overwrite them.
// 3. Which placement each placement was clicked against, to limit how far players can build on blocks the server refused (ghost blocks).
// Server updates are counted on the outbound thread and removed on ack, so the first two maps are concurrent.
public final class BlockPlacementTracker {

    private static final int PLACEMENT_LIFETIME = 200; // How long any client block placement is tracked for
    private static final int MAX_CHAIN_WALK = 100; // Max cap on ghost-block chains to prevent abuse

    private final Map<Vector3i, Integer> unansweredPlacements = new ConcurrentHashMap<>();
    private final Map<Vector3i, Integer> pendingServerUpdates = new ConcurrentHashMap<>();

    // Read on the inbound thread and updated by server block updates on the outbound thread, so every use holds this map's lock.
    private final Map<Vector3i, ChainedPlacement> clientPlacedBlocks = new HashMap<>();

    public void onClientPlace(Vector3i placedPosition, Vector3i clickedPosition, Direction face, int prePlaceRuntimeId) {
        this.unansweredPlacements.put(key(placedPosition), PLACEMENT_LIFETIME);
        if (Boar.getConfig().maxGhostBlockChain() < 0) {
            return;
        }

        synchronized (this.clientPlacedBlocks) {
            final Vector3i placedKey = key(placedPosition);
            final ChainedPlacement old = this.clientPlacedBlocks.get(placedKey);
            // Keep a placement the server refused or has not sent an answer for yet, so placing on the same spot again can't cut the chain built on it.
            if (old != null && !(old.confirmed && old.allowed)) {
                if (old.confirmed) {
                    old.placedAgain = true;
                } else {
                    old.latestPrePlaceRuntimeId = prePlaceRuntimeId;
                }
                return;
            }
            if (old != null) {
                this.clientPlacedBlocks.remove(placedKey);
                old.unlink();
            }

            // A block that replaced the clicked block (tall grass, merging into the clicked slab...) is not next to it, so it starts its own chain.
            final boolean nextToClicked = placedKey.equals(key(clickedPosition).add(face.getUnitVector()));
            final ChainedPlacement parent = nextToClicked ? this.clientPlacedBlocks.get(key(clickedPosition)) : null;
            this.clientPlacedBlocks.put(placedKey, new ChainedPlacement(prePlaceRuntimeId, face, parent));
        }
    }

    // The server refused a placement when it sends back the block that was there before it, or, for a spot placed on twice before any answer, the block before the second placement.
    public void onServerUpdateSent(Vector3i position, int layer, int runtimeId) {
        final Vector3i key = key(position);
        this.pendingServerUpdates.merge(key, 1, Integer::sum);
        if (layer != 0 || Boar.getConfig().maxGhostBlockChain() < 0) {
            return;
        }

        synchronized (this.clientPlacedBlocks) {
            final ChainedPlacement placement = this.clientPlacedBlocks.get(key);
            if (placement != null) {
                placement.setAllowed(runtimeId != placement.prePlaceRuntimeId && runtimeId != placement.latestPrePlaceRuntimeId);
            }
        }
    }

    // True when this block is a ghost block the server refused, and placing against it would make its chain longer than the config allows.
    public boolean exceedsGhostBlockChain(Vector3i position) {
        final int maxChain = Boar.getConfig().maxGhostBlockChain();
        return maxChain >= 0 && this.ghostBlockChainSize(position) >= Math.max(1, maxChain);
    }

    // How many refused placements the chain at this position holds: the one here and the ones under it, up to the first one the server allowed.
    public int ghostBlockChainSize(Vector3i position) {
        synchronized (this.clientPlacedBlocks) {
            int size = 0;
            for (ChainedPlacement node = this.clientPlacedBlocks.get(key(position)); node != null && !node.allowed && size < MAX_CHAIN_WALK; node = node.parent()) {
                size++;
            }
            return size;
        }
    }

    public boolean isUnansweredPlacement(Vector3i position) {
        return this.unansweredPlacements.containsKey(key(position));
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

        synchronized (this.clientPlacedBlocks) {
            this.clientPlacedBlocks.values().removeIf(placement -> {
                if (--placement.remainingTicks > 0) {
                    return false;
                }
                placement.unlink();
                return true;
            });
        }
    }

    // Server packets can give us their own Vector3i class, which doesn't match ours as a map key.
    // Copy every position into the same class so lookups always work.
    private static Vector3i key(Vector3i position) {
        return Vector3i.from(position.getX(), position.getY(), position.getZ());
    }

    public void clearPlacements() {
        this.unansweredPlacements.clear();
        synchronized (this.clientPlacedBlocks) {
            this.clientPlacedBlocks.clear();
        }
    }

    // A client placement, linked to the placement it was clicked against (its parent) and to the placements clicked against it.
    private static final class ChainedPlacement {
        private final int prePlaceRuntimeId;
        private int latestPrePlaceRuntimeId;
        private final Map<Direction, ChainedPlacement> connections = new EnumMap<>(Direction.class);
        @Nullable private Direction parentFace;
        private boolean allowed = true;
        private boolean confirmed;
        private boolean placedAgain;
        private int remainingTicks = PLACEMENT_LIFETIME;

        private ChainedPlacement(int prePlaceRuntimeId, Direction face, @Nullable ChainedPlacement parent) {
            this.prePlaceRuntimeId = prePlaceRuntimeId;
            this.latestPrePlaceRuntimeId = prePlaceRuntimeId;
            if (parent != null) {
                this.parentFace = face.getOpposite();
                this.connections.put(this.parentFace, parent);
                parent.connections.put(face, this);
            }
        }

        @Nullable
        private ChainedPlacement parent() {
            return this.parentFace == null ? null : this.connections.get(this.parentFace);
        }

        // A server answer for one placement also counts for every placement built on it.
        private void setAllowed(boolean allowed) {
            // A server can send several updates for one spot. Once a placement is confirmed as allowed, a later update doesn't undo it.
            if (this.confirmed && this.allowed) {
                return;
            }
            // A yes for a block placed again on a refused spot is for that block only. The blocks built on the refused one stay refused.
            if (this.placedAgain && allowed) {
                this.allowed = true;
                return;
            }

            this.markChain(allowed, 0);
        }

        private void markChain(boolean allowed, int depth) {
            if ((this.confirmed && this.allowed) || depth >= MAX_CHAIN_WALK) {
                return;
            }

            this.allowed = allowed;
            this.confirmed = true;
            this.connections.forEach((direction, child) -> {
                if (direction != this.parentFace) {
                    child.markChain(allowed, depth + 1);
                }
            });
        }

        private void unlink() {
            this.connections.forEach((direction, other) -> {
                final Direction back = direction.getOpposite();
                other.connections.remove(back);
                if (other.parentFace == back) {
                    other.parentFace = null;
                }
            });
            this.connections.clear();
        }
    }
}
