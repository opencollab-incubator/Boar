package ac.boar.anticheat.validator.inventory.click;

import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.compensated.cache.container.ContainerCache;
import ac.boar.anticheat.data.inventory.ItemCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.validator.inventory.ItemTransactionValidator;
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
import java.util.List;

@RequiredArgsConstructor
public class ItemRequestProcessor {
    private final BoarPlayer player;

    private final List<ItemCache> queuedItems = new ArrayList<>();

    public boolean processAll(final ItemStackRequest request) {
        for (final ItemStackRequestAction action : request.getActions()) {
            // System.out.println(action);
            try {
                if (!this.handle(action)) {
                    // We ignore this... for now!
                }
            } catch (Exception ignored) {
                // Honestly, this inventory handling system is actually just half-baked system and I never actually
                // got the motivation to finish it, if you want to, feel free to PR. But for now
                // I'm just going to leave it as it is, it's good enough *for now*.
            }
        }

        this.queuedItems.clear();

        return true;
    }

    public boolean handle(final ItemStackRequestAction action) {
        final CompensatedInventory inventory = player.compensatedInventory;

        final ItemStackRequestActionType type = action.getType();
        final ContainerCache cache = inventory.openContainer;

        switch (type) {
            case CRAFT_CREATIVE -> {
                if (player.gameType != GameType.CREATIVE) {
                    return false;
                }

                final CraftCreativeAction creativeAction = (CraftCreativeAction) action;
                final ItemData item = inventory.getCreativeData().get(creativeAction.getCreativeItemNetworkId());
                if (item == null) {
                    return false;
                }

                // Creative item yay! Also, we have to grab the item definition we stored instead of
                // the one player send to prevent they send some weird shit item to try anything funny.
                this.queuedItems.add(ItemCache.build(inventory, item));
            }

            case CRAFT_RECIPE -> {
                final CraftRecipeAction craftAction = (CraftRecipeAction) action;
                if (cache.getType() == ContainerType.WORKBENCH) {
                    final RecipeData rawRecipe = inventory.getCraftingData().get(craftAction.getRecipeNetworkId());
                    if (rawRecipe == null) {
                        // System.out.println("No recipe found!");
                        break;
                    }

                    final List<ItemData> ingredients = List.of(
                            cache.get(32).getData(), cache.get(33).getData(), cache.get(34).getData(), cache.get(35).getData(),
                            cache.get(36).getData(), cache.get(37).getData(), cache.get(38).getData(),
                            cache.get(39).getData(), cache.get(40).getData());

                    List<ItemData> results = null;

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
                                    return false;
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
                                return false;
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
                    return false;
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
                        return false;
                    }
                }

                // System.out.println("Valid crafting yay! (2)");
            }

            case TAKE, PLACE -> {
                final TransferItemStackRequestAction transferAction = (TransferItemStackRequestAction) action;

                final BundleClickProcessor.BundleResponse response = BundleClickProcessor.processBundleClick(inventory, transferAction);
                if (response.bundle()) {
                    return response.valid();
                }

                final ItemStackRequestSlotData source = transferAction.getSource();
                final ItemStackRequestSlotData destination = transferAction.getDestination();

                final ContainerCache sourceContainer = this.findContainer(source.getContainer());
                final ContainerCache destinationContainer = this.findContainer(destination.getContainer());

                final int sourceSlot = source.getSlot();
                final int destinationSlot = destination.getSlot();

                // From creative menu, crafting or other actions.
                final boolean create = !this.queuedItems.isEmpty() && sourceSlot == 50 && source.getContainer() == ContainerSlotType.CREATED_OUTPUT;

                if (sourceSlot < 0 || destinationSlot < 0 || (sourceSlot >= sourceContainer.getContainerSize() && !create) ||
                        destinationSlot >= destinationContainer.getContainerSize()) {
                    return false;
                }

                int sourceSlotWithoutOffset = sourceSlot - sourceContainer.getOffset();
                int destinationSlotWithoutOffset = destinationSlot - destinationContainer.getOffset();
                if (sourceSlotWithoutOffset < 0 || !create && sourceSlotWithoutOffset >= sourceContainer.getContents().length) {
                    return false;
                }
                if (destinationSlotWithoutOffset < 0 || !create && destinationSlotWithoutOffset >= sourceContainer.getContents().length) {
                    return false;
                }

                final ItemCache sourceData = create ? this.queuedItems.get(0) : sourceContainer.get(sourceSlot);
                final ItemCache destinationData = destinationContainer.get(destinationSlot);

                // Player try to move this item to an already occupied destination, and is sending TAKE/PLACE instead of SWAP.
                // This is not the same item too, so not possible...
                if (!destinationData.getData().isNull() && !ItemTransactionValidator.validate(sourceData.getData(), destinationData.getData())) {
                    // for debugging in case I fucked up.
                    // System.out.println("INVALID DESTINATION!");
                    // System.out.println(sourceData);
                    // System.out.println(destinationSlot);
                    return false;
                }

                int count = transferAction.getCount();
                // Source data is air, or count is invalid.
                // Exempt this if player is grabbing from creative menu....
                if (!(create && player.gameType == GameType.CREATIVE) && (sourceData.getData().isNull() || count <= 0 || count > sourceData.count())) {
//                    System.out.println("INVALID COUNT!"); // for debugging in case I fucked up.
//                    System.out.println("First condition: " + sourceData.getData().isNull());
//                    System.out.println("Count: " + count);
//                    System.out.println("Source Data: " + sourceData);
                    return false;
                }

                count = Math.max(0, count);

                // Now simply move, lol.
                if (!create) {
                    this.remove(sourceContainer, sourceSlot, sourceData, count);
                }

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

                if (sourceSlot < 0 || destinationSlot < 0 || sourceSlot >= sourceContainer.getContainerSize() || destinationSlot >= destinationContainer.getContainerSize()) {
                    return false;
                }

                int sourceSlotWithoutOffset = sourceSlot - sourceContainer.getOffset();
                int destinationSlotWithoutOffset = destinationSlot - destinationContainer.getOffset();
                if (sourceSlotWithoutOffset < 0 || sourceSlotWithoutOffset >= sourceContainer.getContents().length) {
                    return false;
                }
                if (destinationSlotWithoutOffset < 0 || destinationSlotWithoutOffset >= sourceContainer.getContents().length) {
                    return false;
                }

                final ItemCache sourceData = sourceContainer.get(sourceSlot);
                final ItemCache destinationData = destinationContainer.get(destinationSlot);

                // Source/Destination slot is empty! Player is supposed to send TAKE/PLACE instead of SWAP!
                if (sourceData.getData().isNull() || destinationData.getData().isNull()) {
                    // System.out.println("INVALID SWAP!"); // for debugging in case I fucked up.
                    return false;
                }

                // Now simply swap :D
                sourceContainer.set(sourceSlot, destinationData);
                destinationContainer.set(destinationSlot, sourceData);
            }

            case DROP -> {
                final DropAction dropAction = (DropAction) action;
                final int slot = dropAction.getSource().getSlot();
                if (slot < 0 || slot >= cache.getContainerSize()) {
                    return false;
                }

                final ItemStackRequestSlotData source = dropAction.getSource();

                // Player is clicking outside the window to drop.
                if (source.getContainer() == ContainerSlotType.CURSOR) {
                    final ItemCache cursor = inventory.hudContainer.get(0);
                    if (!cursor.getData().isValid() || slot != 0) { // Slot 0 is cursor slot.
                        return false;
                    }

                    this.remove(inventory.hudContainer, 0, cursor, dropAction.getCount());
                } else { // Dropping by pressing Q?
                    final ItemCache data = cache.get(slot);
                    this.remove(cache, slot, data, dropAction.getCount());
                }
            }

            case DESTROY -> {
                final DestroyAction destroyAction = (DestroyAction) action;
                final ItemStackRequestSlotData source = destroyAction.getSource();
                final ContainerCache sourceContainer = this.findContainer(source.getContainer());

                final int slot = source.getSlot();
                if (slot < 0 || slot > sourceContainer.getContainerSize()) {
                    return false;
                }

                final ItemCache itemData = sourceContainer.get(slot);

                if (destroyAction.getCount() > itemData.count()) {
                    return false;
                }

                this.remove(sourceContainer, slot, itemData, destroyAction.getCount());
            }

            case CONSUME -> {
                final ConsumeAction consumeAction = (ConsumeAction) action;
                final ItemStackRequestSlotData source = consumeAction.getSource();
                final ContainerCache sourceContainer = this.findContainer(source.getContainer());

                final int slot = source.getSlot();
                if (slot < 0 || slot > sourceContainer.getContainerSize()) {
                    return false;
                }

                final ItemCache itemData = sourceContainer.get(slot);

                if (consumeAction.getCount() > itemData.count()) {
                    return false;
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
            default -> cache = inventory.openContainer;
        }

        return cache;
    }
}
