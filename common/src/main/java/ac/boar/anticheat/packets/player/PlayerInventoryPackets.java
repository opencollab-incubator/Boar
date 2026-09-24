package ac.boar.anticheat.packets.player;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.types.ContainerOpenAck;
import ac.boar.anticheat.ack.types.CraftingDataAck;
import ac.boar.anticheat.ack.types.CreativeContentAck;
import ac.boar.anticheat.ack.types.HotbarSlotAck;
import ac.boar.anticheat.ack.types.InventoryContentAck;
import ac.boar.anticheat.ack.types.InventorySlotAck;
import ac.boar.anticheat.ack.types.ItemStackResponseAck;
import ac.boar.anticheat.ack.types.UpdateTradeAck;
import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.check.impl.inventory.Inventory;
import ac.boar.anticheat.data.ItemUseTracker;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.validator.inventory.ItemTransactionValidator;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ConsumeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DestroyAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DropAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.SwapAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.TransferItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.packet.*;

import java.util.ArrayList;

public class PlayerInventoryPackets implements PacketListener {
    @Override
    public void onPacketReceived(final CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();
        final CompensatedInventory inventory = player.compensatedInventory;

        switch (event.getPacket()) {
            case InventoryTransactionPacket packet -> {
                try { // In case I messed up.
                    boolean cancelled = !player.transactionValidator.handle(packet);
                    if (cancelled) {
                        final String details = "invalid transaction: " + player.transactionValidator.getFailReason()
                                + " | type=" + packet.getTransactionType()
                                + ", action=" + packet.getActionType()
                                + ", hotbarSlot=" + packet.getHotbarSlot()
                                + ", heldSlot=" + inventory.heldItemSlot
                                + ", blockPos=" + packet.getBlockPosition()
                                + ", blockFace=" + packet.getBlockFace()
                                + ", clickPos=" + packet.getClickPosition()
                                + ", itemInHand=" + ItemTransactionValidator.describe(packet.getItemInHand())
                                + ", actions=" + packet.getActions().size()
                                + ", gameType=" + player.gameType;

                        if (player.disableMitigations()) {
                            player.getCheckHolder().manuallyFail(Inventory.class, details);
                        } else {
                            Boar.debug(player.getSession().name() + ": " + details, Boar.DebugMessage.WARNING);
                        }
                    }
                    event.setCancelled(cancelled && !player.disableMitigations());
                } catch (Exception exception) {
                    Boar.getInstance().getPlatform().logger().error(
                            "Failed to validate inventory transaction for player " + player.getSession().name()
                                    + ": type=" + packet.getTransactionType()
                                    + ", action=" + packet.getActionType()
                                    + ", slot=" + packet.getHotbarSlot()
                                    + ", blockPosition=" + packet.getBlockPosition(),
                            exception);
                }
            }
            case ItemStackRequestPacket packet -> {
                final boolean skipped = inventory.openContainer == null;
                for (final ItemStackRequest request : packet.getRequests()) {
                    Boar.debug(player.getSession().name() + ": item stack " + describeRequest(request)
                            + " | openContainer=" + (inventory.openContainer == null ? "none"
                                    : inventory.openContainer.getType() + ":" + inventory.openContainer.getId())
                            + ", validated=" + !skipped
                            + ", gameType=" + player.gameType, Boar.DebugMessage.INFO);
                }

                // TODO: Reverse engineer the 1.26.30 client and compare against other server software(s) to make sure inventory handling is matching.
                // Also should look into why certain actions are failing to be validated.
                if (!player.transactionValidator.handle(packet)) {
                    final String details = "invalid item stack request: " + player.transactionValidator.getFailReason()
                            + " | requests=" + packet.getRequests().size()
                            + ", openContainer=" + (inventory.openContainer == null ? "none"
                                    : inventory.openContainer.getType() + ":" + inventory.openContainer.getId())
                            + ", gameType=" + player.gameType;

                    if (player.disableMitigations()) {
                        player.getCheckHolder().manuallyFail(Inventory.class, details);
                    } else {
                        Boar.debug(player.getSession().name() + ": " + details, Boar.DebugMessage.WARNING);
                    }
                }
            }
            case InteractPacket packet -> {
                if (player.runtimeEntityId != packet.getRuntimeEntityId()) {
                    return;
                }

                // This is controlled by server as Geyser use server auth.
                if (packet.getAction() == InteractPacket.Action.OPEN_INVENTORY) {
                    // player.compensatedInventory.openContainer = player.compensatedInventory.inventoryContainer;
                }
            }
            case ContainerClosePacket packet -> {
                if (inventory.openContainer == null) {
                    return;
                }

                if (packet.getId() != inventory.openContainer.getId() && packet.getId() != -1) {
                    return;
                }

                inventory.openContainer = null;
            }
            case MobEquipmentPacket packet -> {
                final int newSlot = packet.getHotbarSlot();
                if (player.runtimeEntityId != packet.getRuntimeEntityId()) {
                    return;
                }

                if (newSlot < 0 || newSlot > 8 || packet.getContainerId() != ContainerId.INVENTORY || inventory.heldItemSlot == newSlot) {
                    return;
                }

                inventory.heldItemSlot = newSlot;

                if (player.getItemUseTracker().getItem() != null || player.getFlagTracker().has(EntityFlag.USING_ITEM)) {
                    player.getItemUseTracker().release();
                    player.getItemUseTracker().setDirtyUsing(ItemUseTracker.DirtyUsing.NONE);
                }
            }
            default -> {}
        }
    }

    @Override
    public void onPacketSend(final CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();
        final CompensatedInventory inventory = player.compensatedInventory;

        switch (event.getPacket()) {
            case CreativeContentPacket packet -> player.queueAcknowledgment(new CreativeContentAck(packet.getContents()));
            case CraftingDataPacket packet -> player.queueAcknowledgment(new CraftingDataAck(packet.getCraftingData(), packet.getPotionMixData()));
            case ContainerOpenPacket packet -> player.queueAcknowledgment(new ContainerOpenAck(packet.getId(), packet.getType(), packet.getBlockPosition(), packet.getUniqueEntityId()));
            case UpdateTradePacket packet when packet.getPlayerUniqueEntityId() == player.runtimeEntityId && packet.getContainerType() == ContainerType.TRADE -> {
                player.sendLatencyStack(new UpdateTradeAck((byte) packet.getContainerId(), packet.getContainerType(), packet.getOffers(), packet.getTraderUniqueEntityId()));
            }
            case InventorySlotPacket packet -> player.sendLatencyStack(new InventorySlotAck(packet.getContainerId(), packet.getSlot(), packet.getItem(), packet.getStorageItem()));
            case InventoryContentPacket packet -> player.sendLatencyStack(new InventoryContentAck(packet.getContainerId(), packet.getContents(), packet.getStorageItem()));
            case ItemStackResponsePacket packet -> player.sendLatencyStack(new ItemStackResponseAck(new ArrayList<>(packet.getEntries())));
            case PlayerHotbarPacket packet when packet.getContainerId() == inventory.inventoryContainer.getId() && packet.isSelectHotbarSlot() -> {
                final int slot = packet.getSelectedHotbarSlot();
                if (slot >= 0 && slot < 9) {
                    player.sendLatencyStack(new HotbarSlotAck(slot));
                }
            }
            default -> {}
        }
    }

    private static String describeRequest(final ItemStackRequest request) {
        final StringBuilder builder = new StringBuilder();
        builder.append("req#").append(request.getRequestId()).append(" [");

        final ItemStackRequestAction[] actions = request.getActions();
        for (int i = 0; i < actions.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }

            final ItemStackRequestAction action = actions[i];
            builder.append(action.getType());
            switch (action) {
                case TransferItemStackRequestAction transfer -> builder.append(' ')
                        .append(describeSlot(transfer.getSource()))
                        .append(" -> ")
                        .append(describeSlot(transfer.getDestination()))
                        .append(" x")
                        .append(transfer.getCount());
                case SwapAction swap -> builder.append(' ')
                        .append(describeSlot(swap.getSource()))
                        .append(" <-> ")
                        .append(describeSlot(swap.getDestination()));
                case DropAction drop -> builder.append(' ')
                        .append(describeSlot(drop.getSource()))
                        .append(" x")
                        .append(drop.getCount())
                        .append(drop.isRandomly() ? " randomly" : "");
                case DestroyAction destroy -> builder.append(' ')
                        .append(describeSlot(destroy.getSource()))
                        .append(" x")
                        .append(destroy.getCount());
                case ConsumeAction consume -> builder.append(' ')
                        .append(describeSlot(consume.getSource()))
                        .append(" x").append(consume.getCount());
                default -> {
                }
            }
        }
        return builder.append(']').toString();
    }

    private static String describeSlot(final ItemStackRequestSlotData data) {
        if (data == null) {
            return "null";
        }
        return data.getContainer() + "/" +
                (data.getContainerName() == null ? "?" : data.getContainerName().getContainer())
                + ":" + data.getSlot() + "(net=" + data.getStackNetworkId() + ")";
    }
}
