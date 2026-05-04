package ac.boar.anticheat.validator.inventory;

import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.data.InteractionResult;
import ac.boar.anticheat.data.ItemUseTracker;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.data.inventory.BoarItemStack;
import ac.boar.anticheat.data.inventory.ItemCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.Reference;
import ac.boar.anticheat.util.StringUtil;
import ac.boar.anticheat.util.block.BlockUtil;
import ac.boar.anticheat.util.math.Axis;
import ac.boar.anticheat.util.math.Direction;
import ac.boar.anticheat.validator.inventory.click.ItemRequestProcessor;
import ac.boar.mappings.block.Block;
import ac.boar.mappings.block.BlockMappings;
import ac.boar.mappings.block.Blocks;
import ac.boar.mappings.block.Properties;
import ac.boar.mappings.item.Item;
import ac.boar.mappings.item.ItemMappings;
import ac.boar.mappings.item.Items;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.LegacySetItemSlotData;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public final class ItemTransactionValidator {
    private final BoarPlayer player;

    public boolean handle(final InventoryTransactionPacket packet) {
        final CompensatedInventory inventory = player.compensatedInventory;
        switch (packet.getTransactionType()) {
            case NORMAL -> {
                if (packet.getActions().size() != 2) {
                    return false;
                }
                // https://github.com/GeyserMC/Geyser/blob/master/core/src/main/java/org/geysermc/geyser/translator/protocol/bedrock/BedrockInventoryTransactionTranslator.java#L123
                final InventoryActionData world = packet.getActions().get(0), container = packet.getActions().get(1);

                if (world.getSource().getType() != InventorySource.Type.WORLD_INTERACTION || world.getSource().getFlag() != InventorySource.Flag.DROP_ITEM) {
                    return false;
                }

                final int slot = container.getSlot();
                if (slot < 0 || slot > 8) {
                    return false;
                }

                final ItemData slotData = inventory.inventoryContainer.getItemFromSlot(slot).getData();
                final ItemData claimedData = world.getToItem();
                final int dropCounts = claimedData.getCount();

                // Invalid drop, item or whatever
                if (dropCounts < 1 || dropCounts > slotData.getCount() || !validate(slotData, claimedData)) {
                    return false;
                }

                // Since Geyser proceed to drop everything anyway, as long as you send anything larger than 1.
                // Also, it is possible to drop more than 1 but not all? I don't know.
                if (dropCounts > 1 && dropCounts < slotData.getCount()) {
                    final InventorySlotPacket slotPacket = new InventorySlotPacket();
                    slotPacket.setItem(ItemData.AIR);
                    slotPacket.setContainerId(ContainerId.INVENTORY);
                    slotPacket.setSlot(slot);
//                    player.cloudburstUpstream.sendPacket(slotPacket);
                }

                if (dropCounts == slotData.getCount()) {
                    inventory.inventoryContainer.set(slot, ItemData.AIR);
                } else {
                    ItemData.Builder builder = slotData.toBuilder();
                    builder.count(Math.max(0, slotData.getCount() - dropCounts));

                    inventory.inventoryContainer.set(slot, builder.build());
                }
            }

            case ITEM_RELEASE -> {
                // Self-explanatory.
                if (packet.getActionType() == 0) {
                    if (player.compensatedInventory.inventoryContainer.getHeldItem().is(Items.TRIDENT)) {
                        player.setDirtyRiptide(player.sinceTridentUse, player.compensatedInventory.inventoryContainer.getHeldItemData());
                    }

                    player.getItemUseTracker().release();
                    player.getItemUseTracker().setDirtyUsing(ItemUseTracker.DirtyUsing.NONE);
//                    System.out.println("Release using item.");
                }
            }

            case ITEM_USE -> {
                final Vector3i position = packet.getBlockPosition();
                final int slot = packet.getHotbarSlot();
                if (slot < 0 || slot > 8) {
                    return false;
                }

                final ItemData SD1 = inventory.inventoryContainer.getHeldItemData();

                boolean noActions = packet.getActions().isEmpty();

                if (!noActions) {
                    for (final InventoryActionData action : packet.getActions()) {
                        if (action.getSlot() < 0 || action.getSlot() > 8) {
                            return false;
                        }

                        final ItemData SD2 = inventory.inventoryContainer.getItemFromSlot(action.getSlot()).getData();
                        if (!validate(SD2, action.getFromItem())) {
                            return false;
                        }
                    }
                }

                if (noActions && !validate(SD1, packet.getItemInHand())) {
                    return false;
                }

                float distance = player.position.toVector3f().distanceSquared(position.getX(), position.getY(), position.getZ());
                if (!MathUtil.isValid(position) || distance > 12 * 12 && position.getX() + position.getY() + position.getZ() != 0) {
                    return false;
                }

                // The rest is going to validate by Geyser.

                final BoarBlockState boarState = player.compensatedWorld.getBlockState(position, 0);
                final Block block = boarState.block();
                switch (packet.getActionType()) {
                    case 0 -> { // TODO: Maybe... move this into a separate class?
                        if (packet.getItemInHand() == null || !validate(SD1, packet.getItemInHand())) {
                            return true; // nope, not a mistake, Geyser going to take care of it anyway.
                        }

                        if (packet.getClientInteractPrediction() == ItemUseTransaction.PredictedResult.FAILURE) {
                            return true; // Player claimed to be failing this action, no need to process it.
                        }

                        if (packet.getBlockPosition() == null) {
                            return false;
                        }

                        int blockFace = packet.getBlockFace();
                        if (blockFace < 0 || blockFace > 5) {
                            return false; // Invalid.
                        }

                        ItemCache heldItem = inventory.inventoryContainer.getHeldItemCache();
                        BoarItemStack geyserItemStack = BoarItemStack.of(player.getSession(), heldItem.getData());
                        Item item = geyserItemStack.item();

                        boolean heldItemExist = !heldItem.isEmpty();
                        boolean doingSecondaryAction = player.getInputData().contains(PlayerAuthInputData.SNEAKING) && heldItemExist;

                        if (!doingSecondaryAction) {
                            InteractionResult result = InteractionResult.TRY_WITH_EMPTY_HAND;
                            // useItemOn part.
                            if (boarState.is(Blocks.CAULDRON) &&
                                    (item.is(Items.WATER_BUCKET) || item.is(Items.LAVA_BUCKET) ||
                                            item.is(Items.POWDER_SNOW_BUCKET))) {
                                result = InteractionResult.SUCCESS;
                            }

                            if (boarState.is(Blocks.CAKE) && (ItemMappings.get().getCandleItems().contains(item) || boarState.get(Properties.BITES) == 0)) {
                                result = InteractionResult.SUCCESS;
                            }

                            if (BlockMappings.get().getCandleBlocks().contains(block) && (geyserItemStack.isEmpty() && boarState.get(Properties.LIT))) {
                                result = InteractionResult.SUCCESS;
                            }

                            if (boarState.is(Blocks.CHISELED_BOOKSHELF)) {
//                                if (!tagCache.is(ItemTag.BOOKSHELF_BOOKS, item)) {
//                                    result = InteractionResult.TRY_WITH_EMPTY_HAND;
//                                }
//                                OptionalInt optionalInt = this.getHitSlot(blockHitResult, blockState);
//                                if (optionalInt.isEmpty()) {
//                                    return InteractionResult.PASS;
//                                }
//                                if (((Boolean)blockState.getValue(SLOT_OCCUPIED_PROPERTIES.get(optionalInt.getAsInt()))).booleanValue()) {
//                                    return InteractionResult.TRY_WITH_EMPTY_HAND;
//                                }
//                                ChiseledBookShelfBlock.addBook(level, blockPos, player, chiseledBookShelfBlockEntity, itemStack, optionalInt.getAsInt());
//                                return InteractionResult.SUCCESS;
                            }

                            if (boarState.is(Blocks.LECTERN)) {
                                if (!boarState.get(Properties.HAS_BOOK) && geyserItemStack.isEmpty()) {
                                    result = InteractionResult.SUCCESS;
                                }
                            }

                            if (boarState.is(Blocks.NOTE_BLOCK)) {
                                if (ItemMappings.get().getHeadItems().contains(item) && blockFace == Direction.UP.ordinal()) {
                                    result = InteractionResult.SUCCESS;
                                }
                            }

                            if (boarState.is(Blocks.PUMPKIN) || boarState.is(Blocks.REDSTONE_ORE)) {
                                result = InteractionResult.SUCCESS;
                            }

                            if (boarState.is(Blocks.RESPAWN_ANCHOR)) {
                                if (item.is(Items.GLOWSTONE) && boarState.get(Properties.RESPAWN_ANCHOR_CHARGES) < 4) {
                                    result = InteractionResult.SUCCESS;
                                }
                            }

                            if (BlockMappings.get().getSignBlocks().contains(block)) {
                                result = InteractionResult.SUCCESS;
                            }

                            if (boarState.is(Blocks.SWEET_BERRY_BUSH)) {
                                if (boarState.get(Properties.AGE_3) != 3 && item.is(Items.BONE_MEAL)) {
                                    result = InteractionResult.SUCCESS;
                                }
                            }

                            if (boarState.is(Blocks.TNT)) {
                                if (item.is(Items.FLINT_AND_STEEL) || item.is(Items.FIRE_CHARGE)) {
                                    result = InteractionResult.SUCCESS;
                                }
                            }

                            if (boarState.is(Blocks.VAULT) && (geyserItemStack.isEmpty() || !boarState.get(Properties.VAULT_STATE).equals("active"))) {
                                result = InteractionResult.SUCCESS;
                            }

                            if (result != InteractionResult.TRY_WITH_EMPTY_HAND) {
                                return true;
                            }
                            // useWithoutItem part.

                            if (boarState.is(Blocks.FURNACE) || boarState.is(Blocks.BLAST_FURNACE) || boarState.is(Blocks.ANVIL) ||
                                    boarState.is(Blocks.CHIPPED_ANVIL) || boarState.is(Blocks.DAMAGED_ANVIL) || boarState.is(Blocks.BARREL) ||
                                    boarState.is(Blocks.BEACON) || BlockMappings.get().getBedBlocks().contains(block) || boarState.is(Blocks.BREWING_STAND) ||
                                    BlockMappings.get().getButtonBlocks().contains(block) || boarState.is(Blocks.LEVER)) {
                                result = InteractionResult.SUCCESS;
                            }

                            if (boarState.is(Blocks.BELL) && packet.getClickPosition() != null && isProperHit(boarState, Direction.values()[blockFace], packet.getClickPosition().getY() - packet.getBlockPosition().getY())) {
                                result = InteractionResult.SUCCESS;
                            }

                            if (boarState.get(Properties.OPEN) != null) {
                                player.compensatedWorld.updateBlock(position, 0,
                                        player.mappingInfo.fromIntermediary().applyAsInt(boarState.with(Properties.OPEN, !boarState.get(Properties.OPEN)).intermediaryId()));
                                result = InteractionResult.SUCCESS;
                            }

                            if (result != InteractionResult.TRY_WITH_EMPTY_HAND) {
                                return true;
                            }
                        }

                        Vector3i newBlockPos = BlockUtil.getBlockPosition(packet.getBlockPosition(), packet.getBlockFace());
                        if ((boarState.is(Blocks.SCAFFOLDING) ||
                                player.compensatedWorld.getBlockState(newBlockPos, 0).is(Blocks.SCAFFOLDING))
                                && item.is(Items.SCAFFOLDING)) {
                            return true; // We don't need to compensate for this.
                        }

                        if (boarState.isAir()) {
                            // Player seems to be able to do this... on Vanilla, and even claimed "yeah the block definition for this is air".
                            // Well an advantage is an advantage... resync.
                            BlockUtil.restoreCorrectBlock(player, newBlockPos);
                            BlockUtil.restoreCorrectBlock(player, packet.getBlockPosition());

                            // GeyserBoar.getLogger().severe("AIR PLACEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE");
                            player.tickSinceBlockResync = 5;
                            return false;
                        }

                        if (item.is(Items.WATER_BUCKET)) {
                            player.compensatedWorld.updateBlock(newBlockPos, 0, player.mappingInfo.waterId());

                            BoarItemStack stack = BoarItemStack.of(player.getSession(), Items.BUCKET, 1);
                            inventory.inventoryContainer.set(inventory.heldItemSlot, stack.toItemData(player.getSession()));
                        } else if (item.is(Items.LAVA_BUCKET)) {
                            player.compensatedWorld.updateBlock(newBlockPos, 0, player.mappingInfo.lavaDefinition().getRuntimeId());

                            BoarItemStack stack = BoarItemStack.of(player.getSession(), Items.BUCKET, 1);
                            inventory.inventoryContainer.set(inventory.heldItemSlot, stack.toItemData(player.getSession()));
                        } else if (item.is(Items.POWDER_SNOW_BUCKET)) {
                            player.compensatedWorld.updateBlock(newBlockPos, 0, player.mappingInfo.powderSnowDefinition().getRuntimeId());

                            BoarItemStack stack = BoarItemStack.of(player.getSession(), Items.BUCKET, 1);
                            inventory.inventoryContainer.set(inventory.heldItemSlot, stack.toItemData(player.getSession()));
                        } else if (item.is(Items.BUCKET)) {
                            // int javaId = -1, layer = 0;
                            Reference<Item> itemRef = null;
                            int layer = 0;
                            if (boarState.is(Blocks.WATER)) {
                                itemRef = Items.WATER_BUCKET;
                            } else if (player.compensatedWorld.getBlockState(position, 1).is(Blocks.WATER)) {
                                layer = 1;
                                itemRef = Items.WATER_BUCKET;
                            } else if (boarState.is(Blocks.LAVA)) {
                                itemRef = Items.LAVA_BUCKET;
                            } else if (boarState.is(Blocks.POWDER_SNOW)) {
                                itemRef = Items.POWDER_SNOW_BUCKET;
                            }

                            if (itemRef == null) {
                                return true;
                            }

                            player.compensatedWorld.updateBlock(newBlockPos, layer, player.mappingInfo.airId());

                            BoarItemStack stack = BoarItemStack.of(player.getSession(), itemRef, 1);
                            inventory.inventoryContainer.set(inventory.heldItemSlot, stack.toItemData(player.getSession()));
                        } if (item.isBlock()) { // Handle block item after bucket.
                            Block mappedBlock = BlockMappings.get().getItemToBlock().get(item);
                            if (mappedBlock != null && !mappedBlock.is(Blocks.AIR)) {
                                // System.out.println(player.getSession().getBlockMappings().getBedrockBlock(mappedBlock.defaultBlockState().javaId()));
                                BoarBlockState state1 = BlockUtil.getPlacementState(player, mappedBlock, packet.getBlockPosition());
                                player.compensatedWorld.updateBlock(newBlockPos, 0, player.mappingInfo.fromIntermediary().applyAsInt(state1.intermediaryId()));
                            } else {
                                // System.out.println("What? item=" + blockItem.javaIdentifier());
                            }

                            if (player.gameType != GameType.CREATIVE) {
                                heldItem.count(heldItem.count() - 1);
                                if (heldItem.count() <= 0) {
                                    inventory.inventoryContainer.set(inventory.heldItemSlot, ItemCache.AIR);
                                }
                            }
                        }
                    }

                    // This seems to for things that is not related to block interact and only for item interaction.
                    case 1 -> {
                        if (packet.getItemInHand() == null || !validate(SD1, packet.getItemInHand())) {
                            return true;
                        }

                        BoarItemStack item = BoarItemStack.of(player.getSession(), SD1);
                        if (item.is(Items.FIREWORK_ROCKET) && player.getFlagTracker().has(EntityFlag.GLIDING)) {
//                            player.glideBoostTicks = 20; // Latest geyser break this.
                        }

                        player.getItemUseTracker().use(SD1, item.item(), false);
//                        System.out.println("Dirty using use: " + packet.getItemInHand());

                        List<LegacySetItemSlotData> legacySlots = packet.getLegacySlots();
                        if (packet.getActions().size() == 1 && !legacySlots.isEmpty()) {
                            if (packet.getHotbarSlot() != inventory.heldItemSlot) {
                                break;
                            }

                            LegacySetItemSlotData slotData = legacySlots.get(0);
                            if (slotData.getSlots().length == 0) {
                                break;
                            }

                            int actualSlot = slotData.getSlots()[0];
                            if (actualSlot < 0 || actualSlot >= inventory.armorContainer.getContainerSize()) {
                                break;
                            }

                            if (slotData.getContainerId() == 6) {
                                ItemData oldHotbar = inventory.inventoryContainer.getHeldItemData();
                                inventory.inventoryContainer.set(packet.getHotbarSlot(), inventory.armorContainer.get(actualSlot));
                                inventory.armorContainer.set(actualSlot, oldHotbar);
                            }
                        }
                    }
                }
            }
        }

        return true;
    }

    public void handle(final ItemStackRequestPacket packet) {
        final CompensatedInventory inventory = player.compensatedInventory;
        if (inventory.openContainer == null) {
            return;
        }

        player.doingInventoryAction = true;

        final List<ItemStackRequest> clone = new ArrayList<>(packet.getRequests());
        packet.getRequests().clear();

        final ItemRequestProcessor processor = new ItemRequestProcessor(player);
        for (final ItemStackRequest request : clone) {
            if (request.getActions().length == 0) {
                packet.getRequests().add(request);
                continue;
            }

            if (!processor.processAll(request)) {
                return;
            }

            packet.getRequests().add(request);
        }
    }

    public static boolean validate(final ItemData predicted, final ItemData claimed) {
        if (predicted == null) {
            // Our fault?
            return true;
        }

        if (claimed == null) {
            return false;
        }

        final ItemDefinition ID1 = predicted.getDefinition();
        final ItemDefinition ID2 = claimed.getDefinition();
        if (!(ID1 instanceof SimpleItemDefinition SID1) || !(ID2 instanceof SimpleItemDefinition SID2)) {
            return true;
        }

        if (!StringUtil.sanitizePrefix(SID1.getIdentifier()).equalsIgnoreCase(StringUtil.sanitizePrefix(SID2.getIdentifier()))) {
            return false;
        }

        return ID1.getRuntimeId() == ID2.getRuntimeId();
    }

    public static boolean validate(final ItemDefinition predicted, final ItemDefinition claimed) {
        if (predicted == null) {
            // Our fault?
            return true;
        }

        if (claimed == null) {
            return false;
        }

        if (!StringUtil.sanitizePrefix(predicted.getIdentifier()).equalsIgnoreCase(StringUtil.sanitizePrefix(claimed.getIdentifier()))) {
            return false;
        }

        return predicted.getRuntimeId() == claimed.getRuntimeId();
    }

    private static boolean isProperHit(BoarBlockState blockState, Direction direction, float d) {
        if (direction.getAxis() == Axis.Y || d > 0.8124f) {
            return false;
        }
        Direction direction2 = blockState.get(Properties.HORIZONTAL_FACING);
        String bellAttachType = blockState.get(Properties.BELL_ATTACHMENT);
        return switch (bellAttachType) {
            case "floor" -> direction2.getAxis() == direction.getAxis();
            case "single_wall", "double_wall" -> direction2.getAxis() != direction.getAxis();
            case "ceiling" -> true;
            default -> false;
        };
    }
}
