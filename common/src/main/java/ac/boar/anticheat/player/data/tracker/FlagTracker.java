package ac.boar.anticheat.player.data.tracker;

import ac.boar.anticheat.data.ItemUseTracker;
import ac.boar.anticheat.player.BoarPlayer;
import lombok.Getter;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.EnumSet;
import java.util.Set;

public final class FlagTracker {
    private final Set<EntityFlag> flags = EnumSet.noneOf(EntityFlag.class);
    @Getter
    private boolean flying, wasFlying;
    public void flying(boolean flying) {
        this.flying = this.wasFlying = flying;
    }
    public void setFlying(boolean flying) {
        this.wasFlying = this.flying;
        this.flying = flying;
    }

    public void clear() {
        this.flags.clear();
    }

    public void set(final BoarPlayer player, final Set<EntityFlag> flags) {
        this.set(player, flags, true);
    }

    public void set(final BoarPlayer player, final Set<EntityFlag> flags, boolean server) {
        boolean sneaking = this.has(EntityFlag.SNEAKING), swimming = this.has(EntityFlag.SWIMMING);

        this.clear();
        this.flags.addAll(flags);

        if (server) {
            this.set(EntityFlag.SNEAKING, sneaking);
            this.set(EntityFlag.SWIMMING, swimming);
        }

//        System.out.println("Metadata using: " + flags.contains(EntityFlag.USING_ITEM));
        boolean oldUsingItem = player.getItemUseTracker().getUsedItem() != ItemData.AIR;
        // Don't update this directly, if player actually start using item they will let us know next tick. If the player has already start using, then nothing changed.
        if (this.has(EntityFlag.USING_ITEM)) {
//            System.out.println("Wait for next tick: " + oldUsingItem);
            this.set(EntityFlag.USING_ITEM, oldUsingItem);
            if (!oldUsingItem) {
                player.getItemUseTracker().setDirtyUsing(ItemUseTracker.DirtyUsing.METADATA);
//                System.out.println("Dirty using metadata!");
            }
        } else {
            // If the player send an inventory transaction packet then receive metadata update set using item to false right before next tick, then the
            // START_USING_ITEM should be ignored (bedrock is weird).
            player.getItemUseTracker().setDirtyUsing(ItemUseTracker.DirtyUsing.NONE);
        }
    }

    public void set(final EntityFlag flag, boolean value) {
        if (value) {
            this.flags.add(flag);
        } else {
            this.flags.remove(flag);
        }
    }

    public boolean has(final EntityFlag flag) {
        return flags.contains(flag);
    }

    public EnumSet<EntityFlag> cloneFlags() {
        final EnumSet<EntityFlag> flags = EnumSet.noneOf(EntityFlag.class);
        flags.addAll(this.flags);

        return flags;
    }
}
