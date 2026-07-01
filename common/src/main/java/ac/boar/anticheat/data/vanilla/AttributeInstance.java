package ac.boar.anticheat.data.vanilla;

import lombok.Getter;
import org.cloudburstmc.protocol.bedrock.data.attribute.AttributeModifierData;
import org.cloudburstmc.protocol.bedrock.data.attribute.AttributeOperation;

import java.util.HashMap;
import java.util.Map;

// According to BDS, unlike Java, attribute will update its value instantly after adding/removing an attribute instead
// of setting dirty to true, and dirty will only be set to true IF baseValue is updated.
@Getter
public class AttributeInstance {
    private float baseValue;

    private float value;
    private boolean dirty = true;

    private final Map<String, AttributeModifierData> modifiers = new HashMap<>();

    public AttributeInstance(float baseValue) {
        this.baseValue = baseValue;
    }

    public void setBaseValue(float baseValue) {
        if (this.baseValue == baseValue) {
            return;
        }

        this.baseValue = baseValue;
        this.setDirty();
    }

    public void setValue(float value) {
        this.value = value;
        this.dirty = false;
    }

    public void clearModifiers() {
        if (!this.modifiers.isEmpty()) {
            this.update();
        }

        this.modifiers.clear();
    }

    public void removeModifier(final String id) {
        final AttributeModifierData lv = this.modifiers.remove(id);
        if (lv != null) {
            this.update();
        }
    }

    public void addTemporaryModifier(AttributeModifierData modifier) {
        this.addModifier(modifier);
    }

    private void addModifier(AttributeModifierData modifier) {
        final AttributeModifierData lv = this.modifiers.putIfAbsent(modifier.getId(), modifier);
        if (lv == null) {
            this.update();
        }
    }

    protected void update() {
        this.value = this.computeValue();
    }

    public float getValue() {
        if (this.dirty) {
            this.value = this.computeValue();
            this.dirty = false;
        }

        return this.value;
    }

    public void setDirty() {
        this.dirty = true;
    }

    public AttributeInstance copy() {
        final AttributeInstance instance = new AttributeInstance(this.baseValue);
        instance.modifiers.putAll(this.modifiers);
        instance.value = this.value;
        instance.dirty = this.dirty;

        return instance;
    }

    private float computeValue() {
        float base = this.getBaseValue();
        for (final Map.Entry<String, AttributeModifierData> entry : this.modifiers.entrySet()) {
            final AttributeModifierData modifier = entry.getValue();
            if (modifier.getOperation() == AttributeOperation.ADDITION) {
                base += modifier.getAmount();
            }
        }
        float value = base;
        for (final Map.Entry<String, AttributeModifierData> entry : this.modifiers.entrySet()) {
            final AttributeModifierData modifier = entry.getValue();
            if (modifier.getOperation() == AttributeOperation.MULTIPLY_BASE) {
                value += (base * modifier.getAmount());
            }
        }
        for (final Map.Entry<String, AttributeModifierData> entry : this.modifiers.entrySet()) {
            final AttributeModifierData modifier = entry.getValue();
            if (modifier.getOperation() == AttributeOperation.MULTIPLY_TOTAL) {
                value *= (1.0F + modifier.getAmount());
            }
        }

        return value;
    }
}
