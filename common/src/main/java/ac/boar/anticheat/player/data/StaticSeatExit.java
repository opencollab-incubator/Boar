package ac.boar.anticheat.player.data;

import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.mappings.block.BlockMappings;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;

import java.util.List;

/** Picks the exit spot in the same order the client does when a static seat ejects its rider. The exit position the client claims is never used. */
public final class StaticSeatExit {
    private StaticSeatExit() {}

    public record Result(Vec3 origin, int candidate) {}

    // The 8 exit directions as (x, z), in the client's order: B, -B, B-A, -B-A, A+B, A-B, -A, A.
    // In the client exit code, A and B come from P and Prev (the vehicle's position now and last tick).
    // A static seat has P == Prev, so A = (-1, 0) and B = (0, -1).
    private static final int[][] DIRECTIONS = {{0, -1}, {0, 1}, {1, -1}, {1, 1}, {-1, -1}, {-1, 1}, {1, 0}, {-1, 0}};
    private static final int[] VERTICAL_OFFSETS = {0, 1, -1};

    public static Vector3f seatOrigin(StaticSeatTracker.Actor actor, Vector3f offset) {
        final Vector3f shift = MathUtil.seatShift(actor.yaw, offset);
        return Vector3f.from(actor.origin.getX() + shift.getX(), actor.origin.getY() + shift.getY(), actor.origin.getZ() + shift.getZ());
    }

    /**
     * Null means Boar cannot pick the exit: a block or block read is unknown, or the case is outside this model (a non-finite seat or box,
     * a box width or height of 0 or less, a height above 8, a seat actor with |x| or |z| above 30,000,000 or near the world's min or max Y).
     * The fallback is returned only after all 24 candidates are known to fail.
     */
    public static Result select(BoarPlayer player, StaticSeatTracker.Actor actor) {
        final Vector3f base = seatOrigin(actor, player.seatOffset);
        final Box box = player.boundingBox;
        final float width = Math.min(box.maxX - box.minX, 1F), height = box.maxY - box.minY;
        if (!finite(base.getX(), base.getY(), base.getZ(), width, height) || width <= 0 || height <= 0 || height > 8) {
            return null;
        }
        final int px = (int) Math.floor(actor.origin.getX()), py = (int) Math.floor(actor.origin.getY()), pz = (int) Math.floor(actor.origin.getZ());
        if (Math.abs((double) px) > 30000000 || Math.abs((double) pz) > 30000000) {
            return null;
        }
        if (py < player.compensatedWorld.getMinY() + 2 || py + height + 2 >= player.compensatedWorld.getHeightY()) {
            return null;
        }
        final float centerX = (float) px + .5F, centerZ = (float) pz + .5F;
        try (final var reads = player.compensatedWorld.trackBlockReads()) {
            int index = 0;
            for (int vertical : VERTICAL_OFFSETS) {
                for (int[] direction : DIRECTIONS) {
                    index++;
                    final int x = px + direction[0], y = py + vertical, z = pz + direction[1];
                    final Float floor = floorHeight(player, x, y, z);
                    if (floor == null) {
                        return null;
                    }
                    if (Float.isNaN(floor)) {
                        continue;
                    }
                    final float cx = centerX + direction[0], cz = centerZ + direction[1];
                    final float bottom = (float) y + floor;
                    final Box probe = new Box(cx - width / 2F, bottom, cz - width / 2F, cx + width / 2F, bottom + height, cz + width / 2F);
                    final Boolean clear = clear(player, probe);
                    if (clear == null) {
                        return null;
                    }
                    if (!clear) {
                        continue;
                    }
                    if (!reads.known()) {
                        return null;
                    }
                    // Add in the same order as the client. The check uses the block center. The result uses base + direction.
                    final float resultX = base.getX() + direction[0];
                    float resultY = base.getY() + ((float) py - box.minY);
                    resultY = resultY + floor;
                    resultY = resultY + vertical;
                    final float resultZ = base.getZ() + direction[1];
                    return new Result(new Vec3(resultX, resultY + .001F, resultZ), index);
                }
            }
            if (!reads.known()) {
                return null;
            }
        }
        final float x = box.minX + (box.maxX - box.minX) * .5F;
        final float y = box.minY + (box.maxY - box.minY) * .5F;
        final float z = box.minZ + (box.maxZ - box.minZ) * .5F;
        return new Result(new Vec3(x, (player.getYOffset() + y) + .001F, z), 0);
    }

    private static Float floorHeight(BoarPlayer player, int x, int y, int z) {
        final BoarBlockState current = knownState(player, x, y, z);
        if (current == null) {
            return null;
        }
        final var shapes = coarseShapes(current, player, x, y, z);
        if (shapes.size() > 1) {
            return null;
        }
        if (!shapes.isEmpty() && volume(shapes.getFirst())) {
            final float top = shapes.getFirst().maxY - (float) y;
            // The client takes a partial floor in the current block before it checks if the block is passable.
            if (top < 1F) {
                return top;
            }
            final Boolean passable = passable(current, player);
            if (passable == null) {
                return null;
            }
            if (!passable) {
                return Float.NaN;
            }
        }
        final BoarBlockState below = knownState(player, x, y - 1, z);
        if (below == null) {
            return null;
        }
        final var supports = coarseShapes(below, player, x, y - 1, z);
        if (supports.size() > 1) {
            return null;
        }
        if (supports.isEmpty() || !volume(supports.getFirst())) {
            return Float.NaN;
        }
        final float top = supports.getFirst().maxY - (float) y;
        if (top < 0F) {
            return Float.NaN;
        }
        final Boolean passable = passable(below, player);
        return passable == null ? null : passable ? Float.NaN : top;
    }

    private static Boolean clear(BoarPlayer player, Box box) {
        // The client checks only the cells from floor(min) to floor(max). Overlap must be strict (touching does not count).
        for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++) {
            for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY); y++) {
                for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++) {
                    final BoarBlockState state = knownState(player, x, y, z);
                    if (state == null) {
                        return null;
                    }
                    final var shapes = coarseShapes(state, player, x, y, z);
                    // The client asks for one AABB (box). Do not guess how a multipart mapping fills the gaps.
                    if (shapes.size() > 1) {
                        Box bounds = shapes.getFirst();
                        for (int i = 1; i < shapes.size(); i++) {
                            bounds = bounds.union(shapes.get(i));
                        }
                        if (bounds.intersects(box)) {
                            return null;
                        }
                        continue;
                    }
                    for (Box shape : shapes) {
                        // In the client check, passability matters only if the shapes really overlap.
                        if (shape.intersects(box)) {
                            final Boolean passable = passable(state, player);
                            if (passable == null) {
                                return null;
                            }
                            if (!passable) {
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    // The shapes are not filtered by any box (findCollision with checkAAB false).
    private static List<Box> coarseShapes(BoarBlockState state, BoarPlayer player, int x, int y, int z) {
        if (state.definition(player) instanceof SimpleBlockDefinition definition && definition.getIdentifier().startsWith("minecraft:")
                && state.block() != null && BlockMappings.get().getStairsBlocks().contains(state.block())) {
            // StairBlock inherits BlockType::getCollisionShape -> getVisualShape. Its constructor keeps AABB::BLOCK_SHAPE [0,0,0 -> 1,1,1].
            // This rough exit query does not use the separate stair pieces or the neighbor corner state that normal movement uses.
            return List.of(new Box(x, y, z, x + 1F, y + 1F, z + 1F));
        }
        return state.findCollision(player, Vector3i.from(x, y, z), Box.EMPTY, false);
    }

    private static BoarBlockState knownState(BoarPlayer player, int x, int y, int z) {
        final var raw = player.compensatedWorld.getKnownMappedRawBlockAt(x, y, z, 0);
        final var extra = player.compensatedWorld.getKnownRawBlockAt(x, y, z, 1);
        if (raw.isEmpty() || extra.isEmpty() || !player.mappingInfo.airIds().contains(extra.getAsInt())) {
            return null;
        }
        // getBlockState reads layer 0 through getRawBlockAt, which records that read for KnownBlockReads.
        player.compensatedWorld.observeBlockRead(x, y, z, 1);
        final BoarBlockState state = player.compensatedWorld.getBlockState(x, y, z, 0);
        return state.isWaterlogged() ? null : state;
    }

    /** A block is passable if it is climbable, if it is open (open_bit), or if it is scaffolding or powder snow (the two block ids the client lists). */
    private static Boolean passable(BoarBlockState state, BoarPlayer player) {
        if (!(state.definition(player) instanceof SimpleBlockDefinition definition)) {
            return null;
        }
        final String identifier = definition.getIdentifier();
        // The server's custom block definitions have no block_archetype or traits.
        // The client registers them as plain BlockType (flags 0x200000, climbable bit 47 clear).
        // Other custom definition formats stay unknown until we know their client flags.
        if (!identifier.startsWith("minecraft:") && !identifier.startsWith("cubecraft:")) {
            return null;
        }
        final var block = state.block();
        if (block == null) {
            return null;
        }
        if (BlockMappings.get().getClimbableBlocks().contains(block)) {
            return true;
        }
        if (definition.getState() == null || !(definition.getState().get("states") instanceof NbtMap properties)) {
            return null;
        }
        final Object open = properties.get("open_bit");
        if (open != null) {
            if (!(open instanceof Byte value) || value < 0 || value > 1) {
                return null;
            }
            if (value == 1) {
                return true;
            }
        }
        return identifier.equals("minecraft:scaffolding") || identifier.equals("minecraft:powder_snow");
    }

    private static boolean volume(Box box) {
        return box.minX < box.maxX && box.minY < box.maxY && box.minZ < box.maxZ;
    }

    private static boolean finite(float... values) {
        for (float value : values) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
}
