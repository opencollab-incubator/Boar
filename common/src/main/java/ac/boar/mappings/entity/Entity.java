package ac.boar.mappings.entity;

import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.jetbrains.annotations.Nullable;

public interface Entity {

    EntityDefinition definition();

    @Nullable
    Entity vehicle();

    @Nullable
    Vector3i bedPosition();

    float bbWidth();

    float bbHeight();

    <T> void metadata(EntityDataType<T> type, T value);

    void refreshMetadata();

    void refreshAttributesToSelf();

    void releaseItem();
}
