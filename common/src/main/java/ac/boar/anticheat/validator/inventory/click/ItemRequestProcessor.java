package ac.boar.anticheat.validator.inventory.click;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.compensated.cache.container.ContainerCache;
import ac.boar.anticheat.data.inventory.ItemCache;
import ac.boar.anticheat.data.inventory.SlotSnapshot;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.validator.inventory.ItemTransactionValidator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.RecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapedRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapelessRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DefaultDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.InvalidDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class ItemRequestProcessor {
    private final BoarPlayer player;

    private final List<ItemCache> queuedItems = new ArrayList<>();

    // Collects the reason of every failed action across the whole packet.
    // Used for debug output when mitigations are off. Failures do not stop
    // processing, so the compensated inventory keeps tracking like before.
    @Getter
    private final List<String> failReasons = new ArrayList<>();

    @Getter
    private final List<String> skippedReasons = new ArrayList<>();

    private final Map<SnapshotKey, SlotSnapshot> snapshots = new LinkedHashMap<>();

    private record SnapshotKey(ContainerCache container, int slot) {
    }

    private void snapshot(final ContainerCache cache, final int slot) {
        if (cache == null || !cache.holdsSlot(slot)) {
            return;
        }
        this.snapshots.computeIfAbsent(new SnapshotKey(cache, slot),  key -> new SlotSnapshot(cache, slot, cache.get(slot).clone()));
    }

    private boolean fail(final String reason) {
        this.failReasons.add(reason);
        return false;
    }

    private boolean skip(final String reason) {
        this.skippedReasons.add(reason);
        return true;
    }

    private static boolean slotOutOfBounds(final ContainerCache cache, final int slot) {
        return cache == null || !cache.holdsSlot(slot);
    }

    public boolean processAll(final ItemStackRequest request) {
        final int before = this.failReasons.size();
        this.snapshots.clear();

        if (player.gameType != GameType.CREATIVE) {
            boolean hasCraft = false;
            boolean hasConsume = false;
            for (final ItemStackRequestAction action : request.getActions()) {
                hasCraft |= action.getType() == ItemStackRequestActionType.CRAFT_RECIPE;
                hasConsume |= action.getType() == ItemStackRequestActionType.CONSUME;
            }

            if (hasCraft && !hasConsume) {
                this.failReasons.add("request " + request.getRequestId() + ": CRAFT_RECIPE without CONSUME actions");
            }
        }

        for (int i = 0; i < request.getActions().length; i++) {
            final ItemStackRequestAction action = request.getActions()[i];
            final int actionStart = this.failReasons.size();
            try {
                if (!this.handle(action)) {
                    // We ignore this... for now! The failReasons list still records why it failed.
                }
            } catch (Exception exception) {
                // Honestly, this inventory handling system is actually just half-baked system and I never actually
                // got the motivation to finish it, if you want to, feel free to PR. But for now
                // I'm just going to leave it as it is, it's good enough *for now*.
                this.failReasons.add(action.getType() + ": threw " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
                Boar.getInstance().getPlatform().logger().error(player.getSession().name() + ": item stack action " + action.getType() + " threw", exception);
            }

            // Tag reasons from this action with the request id and action index for the log.
            for (int j = actionStart; j < this.failReasons.size(); j++) {
                this.failReasons.set(j, "request " + request.getRequestId() + " action " + i + ": " + this.failReasons.get(j));
            }
        }

        this.queuedItems.clear();

        if (!this.snapshots.isEmpty()) {
            final Map<Integer, List<SlotSnapshot>> pending = player.compensatedInventory.pendingRequests;
            pending.put(request.getRequestId(), new ArrayList<>(this.snapshots.values()));

            // Prevent abuse against clients not sending back acknowledgments
            while (pending.size() > 64) {
                final Integer evicted = pending.keySet().iterator().next();
                pending.remove(evicted);
                Boar.debug(player.getSession().name() + ": dropped pending item stack request " + evicted, Boar.DebugMessage.WARNING);
            }
        }

        return this.failReasons.size() == before;
    }

    public boolean handle(final ItemStackRequestAction action) {
        final CompensatedInventory inventory = player.compensatedInventory;
        final ItemStackRequestActionType type = action.getType();
        switch (type) {
            case CRAFT_CREATIVE -> {
                if (player.gameType != GameType.CREATIVE) {
                    return fail("CRAFT_CREATIVE: not in creative, gameType=" + player.gameType);
                }

                final CraftCreativeAction creativeAction = (CraftCreativeAction) action;
                final ItemData item = inventory.getCreativeData().get(creativeAction.getCreativeItemNetworkId());
                if (item == null) {
                    return fail("CRAFT_CREATIVE: unknown creative item network id " + creativeAction.getCreativeItemNetworkId());
                }

                // Creative item yay! Also, we have to grab the item definition we stored instead of
                // the one player send to prevent they send some weird shit item to try anything funny.
                this.queuedItems.add(ItemCache.build(inventory, item));
            }

            case CRAFT_RECIPE -> {
                final CraftRecipeAction craftAction = (CraftRecipeAction) action;
                final ContainerCache grid = this.findContainer(ContainerSlotType.CRAFTING_INPUT);
                final boolean workbench = grid != null && grid.getType() == ContainerType.WORKBENCH;
                {
                    final RecipeData rawRecipe = inventory.getCraftingData().get(craftAction.getRecipeNetworkId());
                    if (rawRecipe == null) {
                        // System.out.println("No recipe found!");
                        break;
                    }

                    if (grid == null || grid.getContents() == null) {
                        break;
                    }

                    final List<ItemData> ingredients = new ArrayList<>();
                    for (int i = 0; i < grid.getContents().length; i++) {
                        ingredients.add(grid.get(grid.getOffset() + i).getData());
                    }

                    List<ItemData> results = null;
                    if (!workbench) {
                        final List<ItemDescriptorWithCount> needed = rawRecipe instanceof ShapelessRecipeData shapeless2
                                ? shapeless2.getIngredients()
                                : rawRecipe instanceof ShapedRecipeData shaped2 ? shaped2.getIngredients() : null;
                        if (needed == null) {
                            break;
                        }

                        for (final ItemDescriptorWithCount descriptor : needed) {
                            if (!(descriptor.getDescriptor() instanceof DefaultDescriptor defaultDescriptor)) {
                                continue;
                            }

                            boolean found = false;
                            for (final ItemData item : ingredients) {
                                if (ItemTransactionValidator.validate(item.getDefinition(), defaultDescriptor.getItemId())) {
                                    found = true;
                                }
                            }

                            if (!found) {
                                return fail("CRAFT_RECIPE(grid): missing ingredient "
                                        + ItemTransactionValidator.describe(defaultDescriptor.getItemId())
                                        + ", recipeNetId=" + craftAction.getRecipeNetworkId());
                            }
                        }

                        results = rawRecipe instanceof ShapelessRecipeData shapeless3
                                ? shapeless3.getResults()
                                : ((ShapedRecipeData) rawRecipe).getResults();
                        for (final ItemData data : results) {
                            this.queuedItems.add(ItemCache.build(inventory, data));
                        }
                        break;
                    }

                    // Simple silly crafting validation.
                    if (rawRecipe instanceof ShapelessRecipeData shapeless) {
                        for (final ItemDescriptorWithCount descriptor : shapeless.getIngredients()) {
                            if (descriptor.getDescriptor() instanceof DefaultDescriptor defaultDescriptor) {
                                boolean valid = false;
                                for (final ItemData item : ingredients) {
                                    if (ItemTransactionValidator.validate(item.getDefinition(), defaultDescriptor.getItemId())) {
                                        valid = true;
                                    }
                                }

                                if (!valid) {
                                    // System.out.println("INVALID CRAFTING - SHAPELESS - INGREDIENTS!");
                                    return fail("CRAFT_RECIPE(shapeless): missing ingredient "
                                            + ItemTransactionValidator.describe(defaultDescriptor.getItemId())
                                            + ", recipeNetId=" + craftAction.getRecipeNetworkId());
                                }
                            }
                        }

                        results = shapeless.getResults();
                    } else if (rawRecipe instanceof ShapedRecipeData shaped) {
                        final List<ItemDefinition> predictedIngredients = new ArrayList<>();

                        for (final ItemDescriptorWithCount descriptor : shaped.getIngredients()) {
                            if (descriptor.getDescriptor() instanceof DefaultDescriptor defaultDescriptor) {
                                predictedIngredients.add(defaultDescriptor.getItemId());
                            } else if (descriptor.getDescriptor() instanceof InvalidDescriptor) {
                                predictedIngredients.add(ItemDefinition.AIR);
                            }
                        }

                        for (int i = 0; i < predictedIngredients.size(); i++) {
                            final ItemDefinition predicted = predictedIngredients.get(i);
                            final ItemDefinition claimed = ingredients.get(i).getDefinition();

                            if (!ItemTransactionValidator.validate(predicted, claimed)) {
                                // System.out.println("INVALID CRAFTING - SHAPED - INGREDIENTS!");
                                return fail("CRAFT_RECIPE(shaped): ingredient mismatch at index " + i
                                        + ", predicted=" + ItemTransactionValidator.describe(predicted)
                                        + ", claimed=" + ItemTransactionValidator.describe(claimed)
                                        + ", recipeNetId=" + craftAction.getRecipeNetworkId());
                            }
                        }

                        results = shaped.getResults();
                    }

                    // System.out.println("Valid crafting yay!");
                    if (results != null) {
                        for (final ItemData data : results) {
                            this.queuedItems.add(ItemCache.build(inventory, data));
                        }
                    }
                }
            }

            case CRAFT_RESULTS_DEPRECATED -> {
                if (this.queuedItems.isEmpty()) {
                    return fail("CRAFT_RESULTS: no queued craft results");
                }

                final CraftResultsDeprecatedAction craftResult = (CraftResultsDeprecatedAction) action;

                for (final ItemData item : craftResult.getResultItems()) {
                    boolean valid = false;
                    for (final ItemCache predicted : this.queuedItems) {
                        if (item.isNull()) {
                            continue;
                        }

                        if (ItemTransactionValidator.validate(item, predicted.getData()) && (item.getCount() == predicted.count() ||
                                player.gameType == GameType.CREATIVE)) {
                            valid = true;
                        }
                    }

                    if (!valid) {
                        return fail("CRAFT_RESULTS: unexpected result item " + ItemTransactionValidator.describe(item)
                                + ", queuedResults=" + this.queuedItems.size());
                    }
                }

                // System.out.println("Valid crafting yay! (2)");
            }

            case TAKE, PLACE -> {
                final TransferItemStackRequestAction transferAction = (TransferItemStackRequestAction) action;

                final BundleClickProcessor.BundleResponse response = BundleClickProcessor.processBundleClick(inventory, transferAction);
                if (response.bundle()) {
                    if (!response.valid()) {
                        return fail(type + ": invalid bundle click, source=" + transferAction.getSource().getContainer()
                                + ":" + transferAction.getSource().getSlot()
                                + ", dest=" + transferAction.getDestination().getContainer()
                                + ":" + transferAction.getDestination().getSlot()
                                + ", count=" + transferAction.getCount());
                    }
                    return true;
                }

                final ItemStackRequestSlotData source = transferAction.getSource();
                final ItemStackRequestSlotData destination = transferAction.getDestination();

                final ContainerCache sourceContainer = this.findContainer(source.getContainer());
                final ContainerCache destinationContainer = this.findContainer(destination.getContainer());

                final int sourceSlot = source.getSlot();
                final int destinationSlot = destination.getSlot();

                // From creative menu, crafting or other actions.
                final boolean create = !this.queuedItems.isEmpty() && sourceSlot == 50 && source.getContainer() == ContainerSlotType.CREATED_OUTPUT;

                if (sourceSlot < 0 || destinationSlot < 0) {
                    return fail(type + ": negative slot, source=" + source.getContainer() + ":" + sourceSlot
                            + ", dest=" + destination.getContainer() + ":" + destinationSlot);
                }

                if (!create && slotOutOfBounds(sourceContainer, sourceSlot)) {
                    return skip(type + ": source " + source.getContainer() + ":" + sourceSlot + " may not be properly modelled");
                }
                if (slotOutOfBounds(destinationContainer, destinationSlot)) {
                    return skip(type + ": destination " + destination.getContainer() + ":" + destinationSlot + " may not be properly modelled");
                }

                final ItemCache sourceData = create ? this.queuedItems.get(0) : sourceContainer.get(sourceSlot);
                final ItemCache destinationData = destinationContainer.get(destinationSlot);

                // Player try to move this item to an already occupied destination, and is sending TAKE/PLACE instead of SWAP.
                // This is not the same item too, so not possible...
                if (!destinationData.getData().isNull() && !ItemTransactionValidator.validate(sourceData.getData(), destinationData.getData())) {
                    return fail(type + ": destination holds a different item, sourceSlot=" + sourceSlot
                            + ", destSlot=" + destinationSlot
                            + ", source=" + ItemTransactionValidator.describe(sourceData.getData())
                            + ", dest=" + ItemTransactionValidator.describe(destinationData.getData()));
                }

                int count = transferAction.getCount();
                // Source data is air, or count is invalid.
                // Exempt this if player is grabbing from creative menu....
                if (!(create && player.gameType == GameType.CREATIVE) && (sourceData.getData().isNull() || count <= 0 || count > sourceData.count())) {
                    return fail(type + ": invalid count, requested=" + count
                            + ", available=" + sourceData.count()
                            + ", source=" + ItemTransactionValidator.describe(sourceData.getData())
                            + ", sourceSlot=" + sourceSlot + ", create=" + create);
                }

                count = Math.max(0, count);

                // Now simply move, lol.
                if (!create) {
                    this.remove(sourceContainer, sourceSlot, sourceData, count);
                }

                this.snapshot(destinationContainer, destinationSlot);
                if (destinationData.getData().isNull()) {
                    final ItemCache cache1 = sourceData.clone();
                    cache1.count(count);

                    destinationContainer.set(destinationSlot, cache1);
                } else {
                    this.add(destinationData, count);
                }
            }

            case SWAP -> {
                final SwapAction swapAction = (SwapAction) action;

                final ItemStackRequestSlotData source = swapAction.getSource();
                final ItemStackRequestSlotData destination = swapAction.getDestination();

                final ContainerCache sourceContainer = this.findContainer(source.getContainer());
                final ContainerCache destinationContainer = this.findContainer(destination.getContainer());

                final int sourceSlot = source.getSlot();
                final int destinationSlot = destination.getSlot();

                if (sourceSlot < 0 || destinationSlot < 0) {
                    return fail("SWAP: negative slot, source=" + source.getContainer() + ":" + sourceSlot + ", dest=" + destination.getContainer() + ":" + destinationSlot);
                }

                if (slotOutOfBounds(sourceContainer, sourceSlot)) {
                    return skip("SWAP: source " + source.getContainer() + ":" + sourceSlot + " may not be modelled properly");
                }
                if (slotOutOfBounds(destinationContainer, destinationSlot)) {
                    return skip("SWAP: destination " + destination.getContainer() + ":" + destinationSlot + " may not be modelled properly");
                }

                final ItemCache sourceData = sourceContainer.get(sourceSlot);
                final ItemCache destinationData = destinationContainer.get(destinationSlot);

                // Source/Destination slot is empty! Player is supposed to send TAKE/PLACE instead of SWAP!
                if (sourceData.getData().isNull() || destinationData.getData().isNull()) {
                    return fail("SWAP: empty slot in swap, source=" + ItemTransactionValidator.describe(sourceData.getData())
                            + " at " + sourceSlot + ", dest=" + ItemTransactionValidator.describe(destinationData.getData())
                            + " at " + destinationSlot);
                }

                // Now simply swap :D
                this.snapshot(sourceContainer, sourceSlot);
                this.snapshot(destinationContainer, destinationSlot);
                sourceContainer.set(sourceSlot, destinationData);
                destinationContainer.set(destinationSlot, sourceData);
            }

            case DROP -> {
                final DropAction dropAction = (DropAction) action;
                final ItemStackRequestSlotData source = dropAction.getSource();
                final int slot = source.getSlot();

                // Player is clicking outside the window to drop.
                if (source.getContainer() == ContainerSlotType.CURSOR) {
                    final ItemCache cursor = inventory.hudContainer.get(0);
                    if (!cursor.getData().isValid() || slot != 0) { // Slot 0 is cursor slot.
                        return fail("DROP: invalid cursor drop, slot=" + slot
                                + ", cursor=" + ItemTransactionValidator.describe(cursor.getData())
                                + ", count=" + dropAction.getCount());
                    }

                    this.remove(inventory.hudContainer, 0, cursor, dropAction.getCount());
                } else { // Dropping by pressing Q?
                    final ContainerCache sourceContainer = this.findContainer(source.getContainer());
                    if (slotOutOfBounds(sourceContainer, slot)) {
                        return skip("DROP: source " + source.getContainer() + ":" + slot + " may not be modelled properly");
                    }

                    final ItemCache data = sourceContainer.get(slot);
                    this.remove(sourceContainer, slot, data, dropAction.getCount());
                }
            }

            case DESTROY -> {
                final DestroyAction destroyAction = (DestroyAction) action;
                final ItemStackRequestSlotData source = destroyAction.getSource();
                final ContainerCache sourceContainer = this.findContainer(source.getContainer());

                final int slot = source.getSlot();
                if (slotOutOfBounds(sourceContainer, slot)) {
                    return skip("DESTROY: source " + source.getContainer() + ":" + slot + " may not be modelled properly");
                }

                final ItemCache itemData = sourceContainer.get(slot);

                if (destroyAction.getCount() > itemData.count()) {
                    return fail("DESTROY: count too high, requested=" + destroyAction.getCount()
                            + ", available=" + itemData.count()
                            + ", item=" + ItemTransactionValidator.describe(itemData.getData()) + ", slot=" + slot);
                }

                this.remove(sourceContainer, slot, itemData, destroyAction.getCount());
            }

            case CONSUME -> {
                final ConsumeAction consumeAction = (ConsumeAction) action;
                final ItemStackRequestSlotData source = consumeAction.getSource();
                final ContainerCache sourceContainer = this.findContainer(source.getContainer());

                final int slot = source.getSlot();
                if (slotOutOfBounds(sourceContainer, slot)) {
                    return skip("CONSUME: source " + source.getContainer() + ":" + slot + " may not be modelled properly");
                }

                final ItemCache itemData = sourceContainer.get(slot);

                if (consumeAction.getCount() > itemData.count()) {
                    return fail("CONSUME: count too high, requested=" + consumeAction.getCount()
                            + ", available=" + itemData.count()
                            + ", item=" + ItemTransactionValidator.describe(itemData.getData()) + ", slot=" + slot);
                }

                this.remove(sourceContainer, slot, itemData, consumeAction.getCount());
            }
        }

        return true;
    }

    public void add(final ItemCache data, final int counts) {
        data.count(data.count() + counts);
    }

    private void remove(final ContainerCache cache, final int slot, final ItemCache data, final int counts) {
        this.snapshot(cache, slot);

        if (counts >= data.count()) {
            cache.set(slot, ItemData.AIR);
        } else {
            if (data.count() > 0) {
                if ((data.count() - counts) <= 0) {
                    cache.set(slot, ItemCache.AIR);
                } else {
                    data.count(data.count() - counts);
                }
            }
        }
    }

    private ContainerCache findContainer(final ContainerSlotType type) {
        return findContainer(player.compensatedInventory, type);
    }

    public static ContainerCache findContainer(final CompensatedInventory inventory, final ContainerSlotType type) {
        ContainerCache cache;
        switch (type) {
            case CURSOR -> cache = inventory.hudContainer;
            case ARMOR -> cache = inventory.armorContainer;
            case OFFHAND -> cache = inventory.offhandContainer;
            case INVENTORY, HOTBAR, HOTBAR_AND_INVENTORY -> cache = inventory.inventoryContainer;
            case CRAFTING_INPUT -> {
                final ContainerCache open = inventory.openContainer;
                cache = open != null && open.getType() == ContainerType.WORKBENCH ? open : inventory.craftingGridContainer;
            }
            default -> cache = inventory.openContainer;
        }

        return cache;
    }
}
