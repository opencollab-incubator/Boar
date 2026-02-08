package ac.boar.anticheat.packets.player;

import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.compensated.cache.container.ContainerCache;
import ac.boar.anticheat.compensated.cache.container.impl.TradeContainerCache;
import ac.boar.anticheat.data.inventory.ItemCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.*;
import org.cloudburstmc.protocol.bedrock.packet.*;

import java.util.Objects;

public class PlayerInventoryPackets implements PacketListener {
    @Override
    public void onPacketReceived(final CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();
        final CompensatedInventory inventory = player.compensatedInventory;

        if (event.getPacket() instanceof InventoryTransactionPacket packet) {
            try { // In case I messed up.
                boolean cancelled = !player.transactionValidator.handle(packet);
//                if (cancelled) {
//                    System.out.println("Cancel inventory action: " + packet);
//                }
                event.setCancelled(cancelled);
            } catch (Exception ignored) {}
        }

        if (event.getPacket() instanceof ItemStackRequestPacket packet) {
            player.transactionValidator.handle(packet);
        }

        if (event.getPacket() instanceof InteractPacket packet) {
            if (player.runtimeEntityId != packet.getRuntimeEntityId()) {
                return;
            }

            // This is controlled by server as Geyser use server auth.
            if (packet.getAction() == InteractPacket.Action.OPEN_INVENTORY) {
                // player.compensatedInventory.openContainer = player.compensatedInventory.inventoryContainer;
            }
        }

        if (event.getPacket() instanceof ContainerClosePacket packet) {
            if (inventory.openContainer == null) {
                return;
            }

            if (packet.getId() != inventory.openContainer.getId() && packet.getId() != -1) {
                return;
            }

            inventory.openContainer = null;
        }

        if (event.getPacket() instanceof MobEquipmentPacket packet) {
            final int newSlot = packet.getHotbarSlot();
            if (player.runtimeEntityId != packet.getRuntimeEntityId()) {
                return;
            }

            if (newSlot < 0 || newSlot > 8 || packet.getContainerId() != ContainerId.INVENTORY || inventory.heldItemSlot == newSlot) {
                return;
            }

            inventory.heldItemSlot = newSlot;
        }
    }

    @Override
    public void onPacketSend(final CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();
        final CompensatedInventory inventory = player.compensatedInventory;

        if (event.getPacket() instanceof CreativeContentPacket packet) {
            player.getLatencyUtil().queue(() -> {
                inventory.getCreativeData().clear();

                for (final CreativeItemData data : packet.getContents()) {
                    inventory.getCreativeData().put(data.getNetId(), data.getItem());
                }
            });
        }

        if (event.getPacket() instanceof CraftingDataPacket packet) {
            player.getLatencyUtil().queue(() -> {
                inventory.getCraftingData().clear();

                for (final RecipeData data : packet.getCraftingData()) {
                    switch (data.getType()) {
                        case MULTI -> {
                            final MultiRecipeData recipe = (MultiRecipeData) data;
                            inventory.getCraftingData().put(recipe.getNetId(), recipe);
                        }

                        case SHAPED -> {
                            final ShapedRecipeData recipe = (ShapedRecipeData) data;
                            inventory.getCraftingData().put(recipe.getNetId(), recipe);
                        }

                        case SHAPELESS -> {
                            final ShapelessRecipeData recipe = (ShapelessRecipeData) data;
                            inventory.getCraftingData().put(recipe.getNetId(), recipe);
                        }

                        case SMITHING_TRANSFORM -> {
                            final SmithingTransformRecipeData recipe = (SmithingTransformRecipeData) data;
                            inventory.getCraftingData().put(recipe.getNetId(), recipe);
                        }

                        case SMITHING_TRIM -> {
                            final SmithingTrimRecipeData recipe = (SmithingTrimRecipeData) data;
                            inventory.getCraftingData().put(recipe.getNetId(), recipe);
                        }
                    }
                }
                inventory.setPotionMixData(packet.getPotionMixData());
            });
        }

        if (event.getPacket() instanceof ContainerOpenPacket packet) {
            // System.out.println(packet);
            player.sendLatencyStack();
            player.getLatencyUtil().queue(() -> {
                final ContainerCache container = inventory.getContainer(packet.getId());
                inventory.openContainer = Objects.requireNonNullElseGet(container, () -> new ContainerCache(inventory, packet.getId(), packet.getType(), packet.getBlockPosition(), packet.getUniqueEntityId()));
            });
        }
//
        if (event.getPacket() instanceof UpdateEquipPacket packet) {
//            System.out.println(packet);
//            player.sendLatencyStack();
//            player.getLatencyUtil().addTaskToQueue(player.sentStackId.get(), () -> { try {
//                inventory.openContainer = new ContainerCache((byte) packet.getWindowId(),
//                        ContainerType.from(packet.getWindowType()), Vector3i.ZERO, packet.getUniqueEntityId());
//            } catch (Exception ignored) {}});
        }
//
        if (event.getPacket() instanceof UpdateTradePacket packet) {
            if (packet.getPlayerUniqueEntityId() != player.runtimeEntityId || packet.getContainerType() != ContainerType.TRADE) {
                return;
            }

            player.sendLatencyStack(() -> {
                try {
                    inventory.openContainer = new TradeContainerCache(inventory, packet.getOffers(),
                        (byte) packet.getContainerId(), packet.getContainerType(), Vector3i.ZERO, packet.getTraderUniqueEntityId());
                } catch (Exception ignored) {
                }
            });
        }

        if (event.getPacket() instanceof InventorySlotPacket packet) {
            player.sendLatencyStack(() -> {
                // Bundle should be handled separately.
                if (packet.getContainerId() == 125) {
                    final ItemCache cache;
                    try {
                        cache = inventory.getBundleCache().get(Objects.requireNonNull(packet.getStorageItem().getTag()).getInt("bundle_id"));
                    } catch (Exception ignored) {
                        return;
                    }

                    if (cache == null) {
                        return;
                    }

                    cache.getBundle().getContents()[packet.getSlot()] = ItemCache.build(inventory, packet.getItem());
                    return;
                }

                final ContainerCache container = inventory.getContainer((byte) packet.getContainerId());
                if (container == null) {
                    return;
                }

                if (packet.getSlot() < 0 || packet.getSlot() >= container.getContainerSize()) {
                    return;
                }

                container.set(packet.getSlot(), packet.getItem());
            });
        }

        if (event.getPacket() instanceof InventoryContentPacket packet) {
            player.sendLatencyStack();
            player.sendLatencyStack(() -> {
                // Bundle should be handled separately.
                if (packet.getContainerId() == 125) {
                    final ItemCache cache;
                    try {
                        cache = inventory.getBundleCache().get(Objects.requireNonNull(packet.getStorageItem().getTag()).getInt("bundle_id"));
                    } catch (Exception ignored) {
                        return;
                    }

                    if (cache == null) {
                        return;
                    }

                    for (int i = 0; i < packet.getContents().size(); i++) {
                        // Just in case? Because it seems to be possible to change the size, I will add support for it later.
                        if (i >= 64) {
                            break;
                        }

                        cache.getBundle().getContents()[i] = ItemCache.build(inventory, packet.getContents().get(i));
                    }

                    // System.out.println("Update bundle: " + packet);
                    return;
                }

                final ContainerCache container = inventory.getContainer((byte) packet.getContainerId());
                if (container == null) {
                    return;
                }

                for (int i = 0; i < packet.getContents().size(); i++) {
                    container.set(i, packet.getContents().get(i), false);
                }
            });
        }

        if (event.getPacket() instanceof PlayerHotbarPacket packet) {
            if (packet.getContainerId() != inventory.inventoryContainer.getId() || !packet.isSelectHotbarSlot()) {
                return;
            }

            final int slot = packet.getSelectedHotbarSlot();
            if (slot >= 0 && slot < 9) {
                player.sendLatencyStack(() -> inventory.heldItemSlot = slot);
            }
        }
    }
}
