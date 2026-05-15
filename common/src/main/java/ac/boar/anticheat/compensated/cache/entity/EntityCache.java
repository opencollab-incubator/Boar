package ac.boar.anticheat.compensated.cache.entity;

import ac.boar.anticheat.compensated.cache.entity.state.CachedEntityState;
import ac.boar.anticheat.data.EntityDimensions;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.anticheat.util.reach.PositionInterpolator;
import ac.boar.mappings.entity.EntityDefinition;
import ac.boar.mappings.entity.EntityType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;

@ToString
@RequiredArgsConstructor
@Getter
@Setter
public final class EntityCache {
    private final BoarPlayer player;
    private final EntityType type;
    private final EntityDefinition definition;
    private final long runtimeId;

    private EntityDimensions dimensions;
    private Vec3 serverPosition = Vec3.ZERO;
    private boolean inVehicle;

    private EntityDataMap metadata = new EntityDataMap();

    public void setMetadata(EntityDataMap metadata) {
        this.metadata = metadata;

        if (metadata.containsKey(EntityDataTypes.WIDTH)) {
            this.dimensions = EntityDimensions.fixed(metadata.get(EntityDataTypes.WIDTH), this.definition.height());
        }
        if (metadata.containsKey(EntityDataTypes.HEIGHT)) {
            this.dimensions = EntityDimensions.fixed(this.definition.width(), metadata.get(EntityDataTypes.HEIGHT));
        }
        
        // This is a hacky workaround for boat since boat is real weird when it comes to collision, at least on GeyserMC.
        if (this.definition.identifier().equalsIgnoreCase("minecraft:boat") || this.definition.identifier().equalsIgnoreCase("minecraft:chest_boat")) {
            // This is from debugging which is... ehhhhh, I really don't get why it different from the collision box in behaviour json.
            // TODO: This is still wrong.
            this.dimensions = EntityDimensions.fixed(1.6F, 0.575F);
        }

        if (metadata.containsKey(EntityDataTypes.SCALE)) {
            this.dimensions = this.dimensions.hardScaled(metadata.get(EntityDataTypes.SCALE));
        }
    }

    private CachedEntityState current;

    public boolean affectedByOffset;
    public float getYOffset() {
        if (this.affectedByOffset) {
            return this.definition.offset();
        }

        return 0;
    }

    public void init() {
        this.current = new CachedEntityState(this.player, this);
    }

    public void interpolate(Vec3 pos, boolean lerp) {
        if (!lerp) {
            this.current.setTeleportPos(pos);
        } else {
            final PositionInterpolator lv = this.current.getInterpolator();
            if (lv != null) {
                lv.refreshPositionAndAngles(pos);
            }
        }
    }
}
