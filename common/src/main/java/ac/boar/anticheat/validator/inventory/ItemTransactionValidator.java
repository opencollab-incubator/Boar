package ac.boar.anticheat.validator.inventory;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
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
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Direction;
import ac.boar.anticheat.validator.inventory.click.ItemRequestProcessor;
import ac.boar.mappings.block.Block;
import ac.boar.mappings.block.BlockMappings;
import ac.boar.mappings.block.Blocks;
import ac.boar.mappings.block.Property;
import ac.boar.mappings.block.Properties;
import ac.boar.mappings.item.Item;
import ac.boar.mappings.item.ItemMappings;
import ac.boar.mappings.item.Items;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.codec.v1001.Bedrock_v1001;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
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
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
public final class ItemTransactionValidator {
    private final BoarPlayer player;

    // Holds the reason of the last failed validation. Used for debug output when mitigations are off.
    @Getter
    private String failReason;

    // True if the last transaction is valid but should not reach the server.
    @Getter
    private boolean shouldDropPacket;

    private long lastFailureDebugTick = Long.MIN_VALUE;
    private Vector3i lastFailureDebugPos;

    private boolean fail(final String reason) {
        this.failReason = reason;
        return false;
    }

    public boolean handle(final InventoryTransactionPacket packet) {
        this.failReason = null;
        this.shouldDropPacket = false;

        final CompensatedInventory inventory = player.compensatedInventory;
        switch (packet.getTransactionType()) {
            case NORMAL -> {
                if (packet.getActions().size() != 2) {
                    return fail("NORMAL: expected 2 actions, got " + packet.getActions().size());
                }

                // https://github.com/GeyserMC/Geyser/blob/3aeedfa6f207691d92d4f20106bc586b2ab883d4/core/src/main/java/org/geysermc/geyser/translator/protocol/bedrock/BedrockInventoryTransactionTranslator.java#L134
                boolean isPost26_30 = player.getSession().protocolVersion() >= Bedrock_v1001.CODEC.getProtocolVersion();

                final InventoryActionData world = isPost26_30 ? packet.getActions().get(1) : packet.getActions().get(0);
                final InventoryActionData container = isPost26_30 ? packet.getActions().get(0) : packet.getActions().get(1);

                if (world.getSource().getType() != InventorySource.Type.WORLD_INTERACTION || world.getSource().getFlag() != InventorySource.Flag.DROP_ITEM) {
                    return fail("NORMAL: bad world action, sourceType=" + world.getSource().getType() + ", flag=" + world.getSource().getFlag());
                }

                final int slot = container.getSlot();
                if (slot < 0 || slot > 8) {
                    return fail("NORMAL: container slot out of bounds: " + slot);
                }

                final ItemData slotData = inventory.inventoryContainer.getItemFromSlot(slot).getData();
                final ItemData claimedData = world.getToItem();
                final int dropCounts = claimedData.getCount();

                // Invalid drop, item or whatever
                if (dropCounts < 1 || dropCounts > slotData.getCount() || !validate(slotData, claimedData)) {
                    return fail("NORMAL: invalid drop, slot=" + slot + ", dropCount=" + dropCounts
                            + ", predicted=" + describe(slotData) + ", claimed=" + describe(claimedData));
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
                if (!this.player.compensatedInventory.switchHeldSlot(packet.getHotbarSlot())) {
                    return fail("ITEM_RELEASE: hotbar slot is invalid: " + packet.getHotbarSlot());
                }

                if (packet.getActionType() == 0 || packet.getActionType() == 1) {
                    if (packet.getActionType() == 0 && player.compensatedInventory.inventoryContainer.getHeldItem().is(Items.TRIDENT)) {
                        player.setDirtyRiptide(player.sinceTridentUse, player.compensatedInventory.inventoryContainer.getHeldItemData());
                    }

                    if (player.getFlagTracker().has(EntityFlag.USING_ITEM)) {
                        itemUseDebug("released (action=" + packet.getActionType() + ")");
                    }
                    player.getItemUseTracker().release();
                    player.getItemUseTracker().setDirtyUsing(ItemUseTracker.DirtyUsing.NONE);
                } else {
                    return fail("ITEM_RELEASE: unknown action: " + packet.getActionType());
                }
            }

            case ITEM_USE -> {
                final Vector3i position = packet.getBlockPosition();
                if (!this.player.compensatedInventory.switchHeldSlot(packet.getHotbarSlot())) {
                    return fail("ITEM_USE: hotbar slot is invalid: " + packet.getHotbarSlot());
                }

                final ItemData SD1 = inventory.inventoryContainer.getHeldItemData();
                boolean noActions = packet.getActions().isEmpty();
                if (!noActions) {
                    for (final InventoryActionData action : packet.getActions()) {
                        if (action.getSlot() < 0 || action.getSlot() > 8) {
                            return fail("ITEM_USE: action slot out of bounds: " + action.getSlot());
                        }

                        final ItemData SD2 = inventory.inventoryContainer.getItemFromSlot(action.getSlot()).getData();
                        if (!validate(SD2, action.getFromItem())) {
                            if (isEmpty(SD2)) {
                                continue;
                            }
                            return fail("ITEM_USE: action item mismatch, slot=" + action.getSlot() + ", predicted=" + describe(SD2) + ", claimed=" + describe(action.getFromItem()));
                        }
                    }
                }

                final boolean emptyHandInteraction = isEmpty(SD1) && isEmpty(packet.getItemInHand());
                if (noActions && !emptyHandInteraction && !isEmpty(SD1) && !validate(SD1, packet.getItemInHand())) {
                    return fail("ITEM_USE: held item mismatch, heldSlot=" + inventory.heldItemSlot + ", predicted=" + describe(SD1) + ", claimed=" + describe(packet.getItemInHand()));
                }

                float distance = player.position.toVector3f().distanceSquared(position.getX(), position.getY(), position.getZ());
                if (!MathUtil.isValid(position) || distance > 12 * 12 && position.getX() + position.getY() + position.getZ() != 0) {
                    return fail("ITEM_USE: invalid block position " + position + ", distanceSq=" + distance + ", playerPos=" + player.position);
                }

                // The rest is going to validate by Geyser.

                final BoarBlockState boarState = player.compensatedWorld.getBlockState(position, 0);
                final Block block = boarState.block();
                switch (packet.getActionType()) {
                    case 0 -> { // TODO: Maybe... move this into a separate class?
                        if (packet.getItemInHand() == null) {
                            return true;
                        }

                        if (packet.getClientInteractPrediction() == ItemUseTransaction.PredictedResult.FAILURE) {
                            // Prevent mega block desync
                            final BlockDefinition heldBlock = packet.getItemInHand().getBlockDefinition();
                            final boolean holdsBlock = heldBlock != null && heldBlock.getRuntimeId() != 0 && !player.mappingInfo.airIds().contains(heldBlock.getRuntimeId());
                            this.shouldDropPacket = holdsBlock;

                            if (this.lastFailureDebugTick != player.tick || !position.equals(this.lastFailureDebugPos)) {
                                this.lastFailureDebugTick = player.tick;
                                this.lastFailureDebugPos = position;
                                placementDebug("skipped clicked=" + position + " face=" + packet.getBlockFace() + " reason=client predicted failure dropped=" + holdsBlock);
                            }
                            return true; // Player claimed to be failing this action, no need to process it.
                        }

                        if (packet.getBlockPosition() == null) {
                            return fail("ITEM_USE(place): null block position");
                        }

                        if (player.mappingInfo.isItemFrame(packet.getBlockDefinition())) {
                            return true;
                        }

                        int blockFace = packet.getBlockFace();
                        if (blockFace < 0 || blockFace > 5) {
                            return fail("ITEM_USE(place): invalid block face " + blockFace);
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
                                    BlockMappings.get().getButtonBlocks().contains(block) || BlockMappings.get().getChestBlocks().contains(block) ||
                                    boarState.is(Blocks.LEVER)) {
                                result = InteractionResult.SUCCESS;
                            }

                            if (boarState.is(Blocks.BELL) && packet.getClickPosition() != null && isProperHit(boarState, Direction.values()[blockFace], packet.getClickPosition().getY() - packet.getBlockPosition().getY())) {
                                result = InteractionResult.SUCCESS;
                            }

                            final Boolean open = getOrNull(boarState, Properties.OPEN);
                            if (open != null) {
                                player.compensatedWorld.updateBlock(position, 0,
                                        player.mappingInfo.fromIntermediary().applyAsInt(boarState.with(Properties.OPEN, !open).intermediaryId()));
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

                        if (boarState.isAir() && !player.blockPlacements.isUnansweredPlacement(position)) {
                            // Player seems to be able to do this... on Vanilla, and even claimed "yeah the block definition for this is air".
                            // Well an advantage is an advantage... resync.
                            BlockUtil.restoreCorrectBlock(player, newBlockPos);
                            BlockUtil.restoreCorrectBlock(player, packet.getBlockPosition());

                            placementDebug("denied clicked=" + position + " face=" + blockFace + " reason=clicked block is air, resynced");

                            // At high ping the server can briefly turn a block the client stands on back into air (optimistic block updates for surrounding blocks)
                            // Just keep the click from reaching the server for now
                            this.shouldDropPacket = true;
                            if (Boar.getInstance().getPlatform().developerDebug()) {
                                sendPopup("§cPLACE_AIR (" + position.getX() + ", " + position.getY() + ", " + position.getZ() + ")");
                            }
                            return true;
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
                        }

                        if (item.isBlock()) { // Handle block item after bucket.
                            Block mappedBlock = BlockMappings.get().getItemToBlock().get(item);
                            boolean known = mappedBlock != null && !mappedBlock.is(Blocks.AIR);

                            // Clicking a replaceable block (tall grass, snow layer...) puts the new block in its spot instead of next to it.
                            // Placing the same block on itself doesn't count, same as Java. Air is skipped since here it means
                            // a client block we couldn't model (see isUnansweredPlacement above), not an empty spot.
                            boolean replacesClicked = !boarState.isAir() && boarState.isReplaceable(player) && !(known && boarState.is(mappedBlock));
                            Vector3i placePos = replacesClicked ? position : newBlockPos;

                            if (known) {
                                // System.out.println(player.getSession().getBlockMappings().getBedrockBlock(mappedBlock.defaultBlockState().javaId()));
                                BoarBlockState state1 = BlockUtil.getPlacementState(player, mappedBlock, packet.getBlockPosition());
                                final int placedRuntimeId = player.mappingInfo.fromIntermediary().applyAsInt(state1.intermediaryId());

                                // Don't place block if position is obstructed
                                final String blocker = this.findPlacementBlocker(placePos, placedRuntimeId);
                                if (blocker != null) {
                                    placementDebug("denied placePos=" + placePos + " reason=" + blocker);
                                    BlockUtil.restoreCorrectBlock(player, placePos);
                                    return true;
                                }

                                placementDebug("attempt clicked=" + position + " face=" + blockFace + " placePos=" + placePos
                                        + " replacesClicked=" + replacesClicked + " item=" + describe(heldItem.getData())
                                        + " prediction=" + packet.getClientInteractPrediction() + " playerBox=" + player.boundingBox);

                                player.compensatedWorld.updateBlock(placePos, 0, placedRuntimeId);
                                player.blockPlacements.onClientPlace(placePos);
                                placementDebug("placed placePos=" + placePos + " runtimeId=" + placedRuntimeId);
                            } else {
                                placementDebug("unknown block... not mapped (or inv handling)? item=" + describe(packet.getItemInHand()));
                                if (Boar.getInstance().getPlatform().developerDebug()) {
                                    sendPopup("§cUNMAPPED_ITEM_BLOCK");
                                }
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
                        // Debug: every click-air while we think the player is using an item, to see if the "done eating" click arrives and why it might not count.
                        if (player.getFlagTracker().has(EntityFlag.USING_ITEM)) {
                            final boolean matches = packet.getItemInHand() != null && validate(SD1, packet.getItemInHand());
                            itemUseDebug("click-air while using: heldMatches=" + matches
                                    + " finishing=" + (matches && player.getItemUseTracker().isFinishedConsuming(SD1))
                                    + " elapsed=" + (player.getItemUseTracker().getUseStartTick() < 0 ? "?" : String.valueOf(player.tick - player.getItemUseTracker().getUseStartTick()))
                                    + " claimed=" + describe(packet.getItemInHand()) + " predicted=" + describe(SD1)
                                    + " using=" + describe(player.getItemUseTracker().getUsedItem()));
                        }

                        if (packet.getItemInHand() == null || !validate(SD1, packet.getItemInHand())) {
                            // If for some reason we don't have an item here in Boar's inventory we'll inspect the client-authoritative item. Shouldn't cause
                            // too many issues since it would result in a slow-down if anything with no benefits to a cheater.
                            // TODO: Look into potential inventory bugs
                            if (isEmpty(SD1) && !isEmpty(packet.getItemInHand())) {
                                final BoarItemStack claimed = BoarItemStack.of(player.getSession(), packet.getItemInHand());
                                if (claimed.item() != null) {
                                    this.handleUseClick(packet.getItemInHand(), claimed.item());
                                }
                            }
                            return true;
                        }

                        // Stop the slowdown since the client restarts it itself if it keeps using
                        if (player.getItemUseTracker().isFinishedConsuming(SD1)) {
                            itemUseDebug("finished consuming " + describe(SD1) + " after " + (player.tick - player.getItemUseTracker().getUseStartTick()) + " ticks");
                            player.getItemUseTracker().release();
                            player.getItemUseTracker().setDirtyUsing(ItemUseTracker.DirtyUsing.NONE);
                            return true;
                        }

                        BoarItemStack item = BoarItemStack.of(player.getSession(), SD1);
                        if (item.is(Items.FIREWORK_ROCKET) && player.getFlagTracker().has(EntityFlag.GLIDING)) {
//                            player.glideBoostTicks = 20; // Latest geyser break this.
                        }

                        this.handleUseClick(SD1, item.item());

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

    public boolean handle(final ItemStackRequestPacket packet) {
        this.failReason = null;

        final CompensatedInventory inventory = player.compensatedInventory;
        if (inventory.openContainer == null) {
            return true;
        }

        player.doingInventoryAction = true;

        final List<ItemStackRequest> clone = new ArrayList<>(packet.getRequests());
        final boolean mitigate = !player.disableMitigations();
        if (mitigate) {
            packet.getRequests().clear();
        }

        final ItemRequestProcessor processor = new ItemRequestProcessor(player);
        for (final ItemStackRequest request : clone) {
            if (request.getActions().length == 0) {
                if (mitigate) {
                    packet.getRequests().add(request);
                }
                continue;
            }

            // Failures do not drop the request. We process everything like before,
            // collect every fail reason, and report them all after the loop.
            processor.processAll(request);

            if (mitigate) {
                packet.getRequests().add(request);
            }
        }

        final List<String> skipped = processor.getSkippedReasons();
        if (!skipped.isEmpty()) {
            Boar.debug(player.getSession().name() + ": item stack actions not checked, "  + skipped.size() + " [" + String.join(" | ", skipped) + "]", Boar.DebugMessage.INFO);
        }

        final List<String> reasons = processor.getFailReasons();
        if (!reasons.isEmpty()) {
            return fail("requests=" + clone.size() + ", failed actions=" + reasons.size()
                    + " [" + String.join(" | ", reasons) + "]");
        }

        return true;
    }

    // Entities the client lets you place blocks into (dropped items, orbs, projectiles...).
    private static final Set<String> NON_BLOCKING_ENTITIES = Set.of(
            "minecraft:item", "minecraft:xp_orb", "minecraft:arrow", "minecraft:snowball", "minecraft:egg",
            "minecraft:ender_pearl", "minecraft:thrown_trident", "minecraft:splash_potion", "minecraft:lingering_potion",
            "minecraft:xp_bottle", "minecraft:fishing_hook", "minecraft:fireball", "minecraft:small_fireball",
            "minecraft:dragon_fireball", "minecraft:wither_skull", "minecraft:wither_skull_dangerous",
            "minecraft:shulker_bullet", "minecraft:llama_spit", "minecraft:wind_charge_projectile",
            "minecraft:breeze_wind_charge_projectile", "minecraft:fireworks_rocket", "minecraft:eye_of_ender_signal",
            "minecraft:lightning_bolt", "minecraft:area_effect_cloud", "minecraft:evocation_fang",
            "minecraft:leash_knot", "minecraft:painting"
    );

    // Returns why a block can't go at this spot, or null if it can.
    private String findPlacementBlocker(final Vector3i placePos, final int runtimeId) {
        final BoarBlockState current = player.compensatedWorld.getBlockState(placePos, 0);
        if (!current.isReplaceable(player)) {
            return "spot not replaceable (" + current.block() + ")";
        }

        // Blocks with no collision (torches, flowers...) can go inside the player or entities.
        // The world stores Bedrock IDs, but block states are built from our own IDs, so convert first.
        final BoarBlockState placed = BoarBlockState.create(player.fromRawBlockId(runtimeId), placePos, 0);
        final List<Box> boxes = placed.findCollision(player, placePos, Box.EMPTY, false);
        if (boxes.isEmpty()) {
            return null;
        }

        for (final Box box : boxes) {
            if (box.intersects(player.boundingBox)) {
                return "intersects player box=" + box;
            }
        }

        // Entity positions are lag-compensated, so this is what the client sees.
        for (final EntityCache entity : player.compensatedWorld.getEntities().values()) {
            if (entity.getCurrent() == null || NON_BLOCKING_ENTITIES.contains(entity.getDefinition().identifier())) {
                continue;
            }

            final Box entityBox = entity.getCurrent().getBoundingBox();
            if (entityBox.getLengthX() <= 0 || entityBox.getLengthY() <= 0) {
                continue; // e.g. Geyser's zero-size nametag entities.
            }

            for (final Box box : boxes) {
                if (box.intersects(entityBox)) {
                    return "intersects entity id=" + entity.getRuntimeId() + " type=" + entity.getDefinition().identifier() + " box=" + entityBox;
                }
            }
        }

        return null;
    }

    private void handleUseClick(final ItemData usedItem, final Item item) {
        if (player.getFlagTracker().has(EntityFlag.USING_ITEM)) {
            return;
        }
        if (player.getItemUseTracker().startFromTransaction(usedItem, item)) {
            itemUseDebug("started using " + describe(usedItem));
        }
    }

    private void itemUseDebug(final String message) {
        Boar.debug(player.getSession().name() + ": [item-use-debug] tick=" + player.tick + " " + message, Boar.DebugMessage.INFO);
    }

    private void sendPopup(final String message) {
        final TextPacket packet = new TextPacket();
        packet.setType(TextPacket.Type.POPUP);
        packet.setSourceName("");
        packet.setMessage(message);
        packet.setXuid("");
        player.getConnection().sendPacket(packet);
    }

    private void placementDebug(final String message) {
        Boar.debug(player.getSession().name() + ": [placement-debug] tick=" + player.tick + " " + message, Boar.DebugMessage.INFO);
    }

    // Turn an item into a short readable string for debug messages.
    public static String describe(final ItemData item) {
        if (item == null) {
            return "null";
        }

        return describe(item.getDefinition()) + " x" + item.getCount() + (item.getDamage() != 0 ? " dmg=" + item.getDamage() : "");
    }

    public static String describe(final ItemDefinition definition) {
        if (definition == null) {
            return "?";
        }

        return definition.getIdentifier() + "/" + definition.getRuntimeId();
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

    private static boolean isEmpty(final ItemData itemData) {
        if (itemData == null) {
            return true;
        }

        final int runtimeId = itemData.getDefinition().getRuntimeId();
        return itemData.getCount() <= 0 || runtimeId == 0 || runtimeId == -1;
    }

    private static <T extends Comparable<T>> T getOrNull(final BoarBlockState state, final Property<T> property) {
        try {
            return state.get(property);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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
