package ac.boar.anticheat.validator.inventory;

import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.data.InteractionResult;
import ac.boar.anticheat.data.ItemUseTracker;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.data.inventory.ItemCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.block.BlockUtil;
import ac.boar.anticheat.validator.inventory.click.ItemRequestProcessor;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.StringUtil;
import ac.boar.mappings.BlockMappings;
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
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.BlockItem;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.physics.Axis;
import org.geysermc.geyser.level.physics.Direction;
import org.geysermc.geyser.session.cache.TagCache;
import org.geysermc.geyser.session.cache.tags.BlockTag;
import org.geysermc.geyser.session.cache.tags.ItemTag;
import org.geysermc.geyser.translator.protocol.bedrock.BedrockInventoryTransactionTranslator;
import org.geysermc.geyser.util.BlockUtils;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

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
                    if (player.compensatedInventory.inventoryContainer.getHeldItem().getJavaId() == Items.TRIDENT.javaId()) {
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
                final BlockState state = boarState.getState();
                final Block block = state.block();
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
                        GeyserItemStack geyserItemStack = GeyserItemStack.from(player.getSession(), inventory.translate(heldItem.getData()));
                        Item item = geyserItemStack.asItem();

                        boolean heldItemExist = !heldItem.isEmpty();
                        boolean doingSecondaryAction = player.getInputData().contains(PlayerAuthInputData.SNEAKING) && heldItemExist;

                        if (!doingSecondaryAction) {
                            InteractionResult result = InteractionResult.TRY_WITH_EMPTY_HAND;
                            // useItemOn part.
                            int itemJavaId = item.javaId();
                            if (state.is(Blocks.CAULDRON) &&
                                    (itemJavaId == Items.WATER_BUCKET.javaId() || itemJavaId == Items.LAVA_BUCKET.javaId() ||
                                            itemJavaId == Items.POWDER_SNOW_BUCKET.javaId())) {
                                result = InteractionResult.SUCCESS;
                            }

                            final TagCache tagCache = player.getSession().getTagCache();
                            if (state.is(Blocks.CAKE) && (tagCache.is(ItemTag.CANDLES, item) || state.getValue(Properties.BITES) == 0)) {
                                result = InteractionResult.SUCCESS;
                            }

                            if (tagCache.is(BlockTag.CANDLES, block) && (geyserItemStack.isEmpty() && state.getValue(Properties.LIT))) {
                                result = InteractionResult.SUCCESS;
                            }

                            if (state.is(Blocks.CHISELED_BOOKSHELF)) {
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

                            if (state.is(Blocks.LECTERN)) {
                                if (!state.getValue(Properties.HAS_BOOK) && geyserItemStack.isEmpty()) {
                                    result = InteractionResult.SUCCESS;
                                }
                            }

                            if (state.is(Blocks.NOTE_BLOCK)) {
                                if (tagCache.is(ItemTag.NOTEBLOCK_TOP_INSTRUMENTS, item) && blockFace == Direction.UP.ordinal()) {
                                    result = InteractionResult.SUCCESS;
                                }
                            }

                            if (state.is(Blocks.PUMPKIN) || state.is(Blocks.REDSTONE_ORE)) {
                                result = InteractionResult.SUCCESS;
                            }

                            if (state.is(Blocks.RESPAWN_ANCHOR)) {
                                if (itemJavaId == Items.GLOWSTONE.javaId() && state.getValue(Properties.RESPAWN_ANCHOR_CHARGES) < 4) {
                                    result = InteractionResult.SUCCESS;
                                }
                            }

                            if (tagCache.is(BlockTag.ALL_SIGNS, block)) {
                                result = InteractionResult.SUCCESS;
                            }

                            if (state.is(Blocks.SWEET_BERRY_BUSH)) {
                                if (state.getValue(Properties.AGE_3) != 3 && itemJavaId == Items.BONE_MEAL.javaId()) {
                                    result = InteractionResult.SUCCESS;
                                }
                            }

                            if (state.is(Blocks.TNT)) {
                                if (itemJavaId == Items.FLINT_AND_STEEL.javaId() || itemJavaId == Items.FIRE_CHARGE.javaId()) {
                                    result = InteractionResult.SUCCESS;
                                }
                            }

                            if (state.is(Blocks.VAULT) && (geyserItemStack.isEmpty() || !state.getValue(Properties.VAULT_STATE).equals("active"))) {
                                result = InteractionResult.SUCCESS;
                            }

                            if (result != InteractionResult.TRY_WITH_EMPTY_HAND) {
                                return true;
                            }
                            // useWithoutItem part.

                            if (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.ANVIL) ||
                                    state.is(Blocks.CHIPPED_ANVIL) || state.is(Blocks.DAMAGED_ANVIL) || state.is(Blocks.BARREL) ||
                                    state.is(Blocks.BEACON) || tagCache.is(BlockTag.BEDS, block) || state.is(Blocks.BREWING_STAND) ||
                                    tagCache.is(BlockTag.BUTTONS, block) || state.is(Blocks.LEVER)) {
                                result = InteractionResult.SUCCESS;
                            }

                            if (state.is(Blocks.BELL) && packet.getClickPosition() != null && isProperHit(state, Direction.values()[blockFace], packet.getClickPosition().getY() - packet.getBlockPosition().getY())) {
                                result = InteractionResult.SUCCESS;
                            }

                            if (state.getValue(Properties.OPEN) != null) {
                                player.compensatedWorld.updateBlock(position, 0, player.getSession().getBlockMappings().
                                        getBedrockBlockId(state.withValue(Properties.OPEN, !state.getValue(Properties.OPEN)).javaId()));
                                result = InteractionResult.SUCCESS;
                            }

                            if (result != InteractionResult.TRY_WITH_EMPTY_HAND) {
                                return true;
                            }
                        }

                        Vector3i newBlockPos = BlockUtil.getBlockPosition(packet.getBlockPosition(), packet.getBlockFace());
                        if ((state.is(Blocks.SCAFFOLDING) ||
                                player.compensatedWorld.getBlockState(newBlockPos, 0).getState().is(Blocks.SCAFFOLDING))
                                && item.javaId() == Items.SCAFFOLDING.javaId()) {
                            return true; // We don't need to compensate for this.
                        }

                        if (boarState.isAir()) {
                            // Player seems to be able to do this... on Vanilla, and even claimed "yeah the block definition for this is air".
                            // Well an advantage is an advantage... resync.
                            BlockUtil.restoreCorrectBlock(player.getSession(), newBlockPos);
                            BlockUtil.restoreCorrectBlock(player.getSession(), packet.getBlockPosition());

                            // GeyserBoar.getLogger().severe("AIR PLACEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE");
                            player.tickSinceBlockResync = 5;
                            return false;
                        }

                        if (item.javaId() == Items.WATER_BUCKET.javaId()) {
                            player.compensatedWorld.updateBlock(newBlockPos, 0, player.getSession().getBlockMappings().getBedrockWater().getRuntimeId());

                            GeyserItemStack stack = GeyserItemStack.of(player.getSession(), Items.BUCKET.javaId(), 1);
                            inventory.inventoryContainer.set(inventory.heldItemSlot, inventory.translate(stack.getItemStack()));
                        } else if (item.javaId() == Items.LAVA_BUCKET.javaId()) {
                            player.compensatedWorld.updateBlock(newBlockPos, 0, player.getSession().getBlockMappings().getBedrockBlockId(Blocks.LAVA.
                                    defaultBlockState().javaId()));

                            GeyserItemStack stack = GeyserItemStack.of(player.getSession(), Items.BUCKET.javaId(), 1);
                            inventory.inventoryContainer.set(inventory.heldItemSlot, inventory.translate(stack.getItemStack()));
                        } else if (item.javaId() == Items.POWDER_SNOW_BUCKET.javaId()) {
                            player.compensatedWorld.updateBlock(newBlockPos, 0, player.getSession().getBlockMappings().getBedrockBlockId(Blocks.POWDER_SNOW.defaultBlockState().javaId()));

                            GeyserItemStack stack = GeyserItemStack.of(player.getSession(), Items.BUCKET.javaId(), 1);
                            inventory.inventoryContainer.set(inventory.heldItemSlot, inventory.translate(stack.getItemStack()));
                        } else if (item.javaId() == Items.BUCKET.javaId()) {
                            int javaId = -1, layer = 0;
                            if (state.is(Blocks.WATER)) {
                                javaId = Items.WATER_BUCKET.javaId();
                            } else if (player.compensatedWorld.getBlockState(position, 1).getState().is(Blocks.WATER)) {
                                layer = 1;
                                javaId = Items.WATER_BUCKET.javaId();
                            } else if (state.is(Blocks.LAVA)) {
                                javaId = Items.LAVA_BUCKET.javaId();
                            } else if (state.is(Blocks.POWDER_SNOW)) {
                                javaId = Items.POWDER_SNOW_BUCKET.javaId();
                            }

                            if (javaId == -1) {
                                return true;
                            }

                            player.compensatedWorld.updateBlock(newBlockPos, layer, player.getSession().getBlockMappings().getBedrockAir().getRuntimeId());

                            GeyserItemStack stack = GeyserItemStack.of(player.getSession(), javaId, 1);
                            inventory.inventoryContainer.set(inventory.heldItemSlot, inventory.translate(stack.getItemStack()));
                        } if (item instanceof BlockItem blockItem) { // Handle block item after bucket.
                            Block mappedBlock = BlockMappings.getItemToBlock().getOrDefault(blockItem, Blocks.AIR);
                            if (mappedBlock.javaId() != Blocks.AIR.javaId()) {
                                // System.out.println(player.getSession().getBlockMappings().getBedrockBlock(mappedBlock.defaultBlockState().javaId()));
                                BlockState state1 = BlockUtil.getPlacementState(player, mappedBlock, packet.getBlockPosition());
                                player.compensatedWorld.updateBlock(newBlockPos, 0, player.getSession().getBlockMappings().getBedrockBlockId(state1.javaId()));
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

                        ItemStack item = player.compensatedInventory.translate(SD1);
                        if (item.getId() == Items.FIREWORK_ROCKET.javaId() && player.getFlagTracker().has(EntityFlag.GLIDING)) {
//                            player.glideBoostTicks = 20; // Latest geyser break this.
                        }

                        player.getItemUseTracker().use(SD1, item.getId(), false);
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

    private static boolean isProperHit(BlockState blockState, Direction direction, float d) {
        if (direction.getAxis() == Axis.Y || d > 0.8124f) {
            return false;
        }
        Direction direction2 = blockState.getValue(Properties.HORIZONTAL_FACING);
        String bellAttachType = blockState.getValue(Properties.BELL_ATTACHMENT);
        return switch (bellAttachType) {
            case "floor" -> direction2.getAxis() == direction.getAxis();
            case "single_wall", "double_wall" -> direction2.getAxis() != direction.getAxis();
            case "ceiling" -> true;
            default -> false;
        };
    }
}