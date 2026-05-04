package ac.boar.geyser.mappings.entity;

import ac.boar.mappings.entity.Entity;
import ac.boar.mappings.entity.EntityDefinition;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;

public record GeyserEntity(org.geysermc.geyser.entity.type.Entity handle) implements Entity {

    @Override
    public EntityDefinition definition() {
        return new GeyserEntityDefinition(this.handle.getDefinition());
    }

    @Override
    public Entity vehicle() {
        org.geysermc.geyser.entity.type.Entity vehicle = this.handle.getVehicle();
        if (vehicle == null) {
            return null;
        }

        return new GeyserEntity(vehicle);
    }

    @Override
    public Vector3i bedPosition() {
        if (!(this.handle instanceof PlayerEntity playerEntity)) {
            return null;
        }

        return playerEntity.getBedPosition();
    }

    @Override
    public float bbWidth() {
        return this.handle.getBoundingBoxWidth();
    }

    @Override
    public float bbHeight() {
        return this.handle.getBoundingBoxHeight();
    }

    @Override
    public <T> void metadata(EntityDataType<T> type, T value) {
        this.handle.getDirtyMetadata().put(type, value);
    }

    @Override
    public void refreshMetadata() {
        this.handle.updateBedrockMetadata();
    }

    @Override
    public void refreshAttributesToSelf() {
        if (!(this.handle instanceof SessionPlayerEntity sessionEntity)) {
            return;
        }

        UpdateAttributesPacket attributesPacket = new UpdateAttributesPacket();
        attributesPacket.setRuntimeEntityId(this.handle.geyserId());
        attributesPacket.getAttributes().addAll(sessionEntity.getAttributes().values());
        sessionEntity.getSession().sendUpstreamPacket(attributesPacket);
    }

    @Override
    public void releaseItem() {
        if (this.handle instanceof SessionPlayerEntity sessionEntity) {
            sessionEntity.getSession().releaseItem();
        }
    }
}
