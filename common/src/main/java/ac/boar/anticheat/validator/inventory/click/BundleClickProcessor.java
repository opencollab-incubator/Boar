package ac.boar.anticheat.validator.inventory.click;

import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.compensated.cache.container.ContainerCache;
import ac.boar.anticheat.data.inventory.ItemCache;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.TransferItemStackRequestAction;

public final class BundleClickProcessor {
    public static BundleResponse processBundleClick(final CompensatedInventory inventory, final TransferItemStackRequestAction action) {
        // Not a bundle, handle normally.
        if (!isBundle(action.getDestination()) && !isBundle(action.getSource())) {
            return new BundleResponse(false, false);
        }

        if (isBundle(action.getDestination()) && isBundle(action.getSource())) {
            // System.out.println("NOT POSSIBLE!");
            return new BundleResponse(true, false);
        }

        final ItemStackRequestSlotData source = action.getSource();
        final ItemStackRequestSlotData destination = action.getDestination();

        final int sourceSlot = source.getSlot();
        final int destinationSlot = destination.getSlot();

        if (sourceSlot < 0 || destinationSlot < 0 || action.getCount() <= 0) {
            return new BundleResponse(true, false);
        }

        final int count = action.getCount();

        // Player is trying to release the item...
        if (isBundle(action.getSource())) {
            final ContainerCache destinationContainer = ItemRequestProcessor.findContainer(inventory, destination.getContainer());

            if (sourceSlot > 64 || destinationSlot >= destinationContainer.getContainerSize()) {
                return new BundleResponse(true, false);
            }

            final ItemCache bundleItem = inventory.getBundleCache().get(source.getContainerName().getDynamicId());
            if (bundleItem == null) {
                // System.out.println("CAN'T FIND BUNDLE CACHE!");
                return new BundleResponse(true, false);
            }

            final ItemCache cache = bundleItem.getBundle().getContents()[sourceSlot];
            if (cache.getData().isNull() || count > cache.count()) {
                // System.out.println("INVALID COUNT!");
                // System.out.println(cache.getData());
                return new BundleResponse(true, false);
            }

            if (cache.count() == count) {
                // Remove this item completely and move it to the destination.
                destinationContainer.set(destinationSlot, cache);
                bundleItem.getBundle().getContents()[sourceSlot] = ItemCache.AIR;
            } else {
                cache.count(cache.count() - count);
                destinationContainer.set(destinationSlot, cache.clone().count(count));
            }

            bundleItem.getBundle().count -= count;
        } else {
            // Moving an item into the bundle!
            final ContainerCache sourceContainer = ItemRequestProcessor.findContainer(inventory, source.getContainer());

            if (destinationSlot > 64 || sourceSlot >= sourceContainer.getContainerSize()) {
                return new BundleResponse(true, false);
            }

            final ItemCache bundleItem = inventory.getBundleCache().get(destination.getContainerName().getDynamicId());
            if (bundleItem == null) {
                // System.out.println("CAN'T FIND BUNDLE CACHE!");
                return new BundleResponse(true, false);
            }

            final ItemCache sourceCache = sourceContainer.get(sourceSlot);
            if (sourceCache.getData().isNull() || count > sourceCache.getData().getCount()) {
                // System.out.println("invalid lol!");
                return new BundleResponse(true, false);
            }

            boolean valid = bundleItem.getBundle().add(destinationSlot, sourceCache);
            if (valid) {
                if (count == sourceCache.count()) {
                    sourceContainer.set(sourceSlot, ItemCache.AIR);
                } else {
                    sourceContainer.set(sourceSlot, sourceCache.count(sourceCache.count() - count));
                }
            }

            return new BundleResponse(true, valid);
        }

        return new BundleResponse(true, true);
    }

    private static boolean isBundle(final ItemStackRequestSlotData request) {
        return request.getContainerName().getContainer() == ContainerSlotType.DYNAMIC_CONTAINER;
    }

    public record BundleResponse(boolean bundle, boolean valid) {}
}
