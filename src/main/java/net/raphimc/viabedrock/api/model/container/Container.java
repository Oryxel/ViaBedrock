/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * Copyright (C) 2023-2025 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.raphimc.viabedrock.api.model.container;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.libs.mcstructs.text.TextComponent;
import net.raphimc.viabedrock.ViaBedrock;
import net.raphimc.viabedrock.api.model.container.player.InventoryContainer;
import net.raphimc.viabedrock.api.model.entity.Entity;
import net.raphimc.viabedrock.protocol.ServerboundBedrockPackets;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.*;
import net.raphimc.viabedrock.protocol.data.enums.java.ClickType;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.InventoryAction;
import net.raphimc.viabedrock.protocol.model.InventorySource;
import net.raphimc.viabedrock.protocol.rewriter.ItemRewriter;
import net.raphimc.viabedrock.protocol.storage.GameSessionStorage;
import net.raphimc.viabedrock.protocol.storage.InventoryTracker;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public abstract class Container {

    protected final UserConnection user;
    protected final byte containerId;
    protected final ContainerType type;
    protected final TextComponent title;
    protected final BlockPosition position;
    protected final BedrockItem[] items;
    protected final Set<String> validBlockTags;
    protected final Entity attachedEntity;

    public Container(final UserConnection user, final byte containerId, final ContainerType type, final TextComponent title, final BlockPosition position, final int size, Entity attachedEntity, final String... validBlockTags) {
        this.user = user;
        this.containerId = containerId;
        this.type = type;
        this.title = title;
        this.position = position;
        this.items = BedrockItem.emptyArray(size);
        this.attachedEntity = attachedEntity;
        this.validBlockTags = Set.of(validBlockTags);
    }

    protected Container(final UserConnection user, final byte containerId, final ContainerType type, final TextComponent title, final BlockPosition position, final BedrockItem[] items, final Set<String> validBlockTags, Entity attachedEntity) {
        this.user = user;
        this.containerId = containerId;
        this.type = type;
        this.title = title;
        this.position = position;
        this.items = items;
        this.validBlockTags = validBlockTags;
        this.attachedEntity = attachedEntity;
    }

    public boolean handleClick(PacketWrapper wrapper, final int revision, final short slot, final byte button, final ClickType action) {
        final InventoryTracker inventoryTracker = this.user.get(InventoryTracker.class);

        if (!user.get(GameSessionStorage.class).isInventoryServerAuthoritative()) {
            wrapper.setPacketType(ServerboundBedrockPackets.INVENTORY_TRANSACTION);
            wrapper.write(BedrockTypes.VAR_INT, 0); // legacy request id
            wrapper.write(BedrockTypes.UNSIGNED_VAR_INT, ComplexInventoryTransaction_Type.NormalTransaction.getValue()); // transaction type

            // Seems to be the case, server expect us to send back id 0 despite whatever id server send to us.
            final int containerId = this instanceof InventoryContainer ? ContainerID.CONTAINER_ID_INVENTORY.getValue() : this.containerId();

            final int bedrockSlot = this.bedrockSlot(slot);

            final List<InventoryAction> actions = new ArrayList<>();
            switch (action) {
                case PICKUP -> {
                    if (button < 0 || button > 1) {
                        return false;
                    }

                    if (slot == -999) { // Drop item outside of inventory.

                    } else if (slot >= 0) {
                        if (bedrockSlot >= this.items.length) {
                            ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Tried to translate container click but slot was out of bounds (" + bedrockSlot + ")");
                            return false;
                        }
                        BedrockItem clickedItem = this.getItem(bedrockSlot);
                        BedrockItem cursorItem = inventoryTracker.getHudContainer().getItem(0);
                        if (clickedItem.isEmpty()) {
                            if (!cursorItem.isEmpty()) {
                                // TODO: Bundle item.
//                                if (cursor.getItem() instanceof BundleItem && cursor.has(DataComponents.BUNDLE_CONTENTS) && packet.buttonNum() != 0) {
//                                    action = cursor.get(DataComponents.BUNDLE_CONTENTS).isEmpty() ? InventoryAction.NOTHING : InventoryAction.PLACE_FROM_BUNDLE;
//                                    break;
//                                }

                                BedrockItem toCursorItem;
                                if (button == 0) {
                                    toCursorItem = BedrockItem.empty();
                                } else {
                                    if (cursorItem.amount() - 1 == 0) {
                                        toCursorItem = BedrockItem.empty();
                                    } else {
                                        toCursorItem = cursorItem.copy();
                                        toCursorItem.setAmount(toCursorItem.amount() - 1);
                                    }
                                }

                                // Remove the item from cursor.
                                actions.add(new InventoryAction(
                                        new InventorySource(InventorySourceType.ContainerInventory, ContainerID.CONTAINER_ID_PLAYER_ONLY_UI.getValue(), InventorySource_InventorySourceFlags.NoFlag),
                                        0, cursorItem, toCursorItem
                                ));

                                // Set item!
                                inventoryTracker.getHudContainer().setItem(0, toCursorItem);

                                BedrockItem toSlotItem = cursorItem.copy();
                                if (button == 1) {
                                    toSlotItem.setAmount(1);
                                }

                                // And add the item into the slot
                                actions.add(new InventoryAction(
                                        new InventorySource(InventorySourceType.ContainerInventory, containerId, InventorySource_InventorySourceFlags.NoFlag),
                                        bedrockSlot, clickedItem, toSlotItem
                                ));
                                System.out.println("Slot: " + bedrockSlot + " containerId= " + containerId);

                                this.setItem(bedrockSlot, toSlotItem);
                            }
                        } else {
//                            if (!slot.mayPickup(this.player)) break; This isn't needed since if player don't pick up anything they won't send anything I think?
                            if (cursorItem.isEmpty()) {
                                // TODO: again, bundle
//                                if (slot.getItem().getItem() instanceof BundleItem && slot.getItem().has(DataComponents.BUNDLE_CONTENTS) && packet.buttonNum() != 0) {
//                                    action = slot.getItem().get(DataComponents.BUNDLE_CONTENTS).isEmpty() ? InventoryAction.NOTHING : InventoryAction.PICKUP_FROM_BUNDLE;
//                                    break;
//                                }

                                int pickupAmount;
                                BedrockItem toSlotItem;
                                if (button == 0) {
                                    toSlotItem = BedrockItem.empty();
                                    pickupAmount = clickedItem.amount();
                                } else {
                                    toSlotItem = clickedItem.copy();
                                    int amount = clickedItem.amount();
                                    if (amount / 2 == 0) {
                                        toSlotItem = BedrockItem.empty();
                                        pickupAmount = amount;
                                    }else if (amount % 2 == 0) {
                                        toSlotItem.setAmount(amount / 2);
                                        pickupAmount = amount / 2;
                                    } else {
                                        int split = amount / 2;
                                        toSlotItem.setAmount(split);
                                        pickupAmount = amount - split;
                                    }
                                }

                                // Remove/Split the item from the clicked slot.
                                actions.add(new InventoryAction(
                                        new InventorySource(InventorySourceType.ContainerInventory, containerId, InventorySource_InventorySourceFlags.NoFlag),
                                        bedrockSlot, clickedItem, toSlotItem
                                ));

                                this.setItem(bedrockSlot, toSlotItem);

                                BedrockItem toCursorItem = clickedItem.copy();
                                toCursorItem.setAmount(pickupAmount);

                                actions.add(new InventoryAction(
                                        new InventorySource(InventorySourceType.ContainerInventory, ContainerID.CONTAINER_ID_PLAYER_ONLY_UI.getValue(), InventorySource_InventorySourceFlags.NoFlag),
                                        0, cursorItem, toCursorItem
                                ));

                                inventoryTracker.getHudContainer().setItem(0, toCursorItem);
                            }
                        }
                    } else {
                        return false;
                    }
                }
            }

            if (actions.isEmpty()) {
                return false;
            }

            Type<BedrockItem> bedrockItemType = wrapper.user().get(ItemRewriter.class).itemType();

            wrapper.write(BedrockTypes.UNSIGNED_VAR_INT, actions.size()); // actions count
            for (InventoryAction inventoryAction : actions) {
                wrapper.write(BedrockTypes.INVENTORY_SOURCE, inventoryAction.source()); // inventory source
                wrapper.write(BedrockTypes.UNSIGNED_VAR_INT, inventoryAction.slot());
                wrapper.write(bedrockItemType, inventoryAction.from());
                wrapper.write(bedrockItemType, inventoryAction.to());
            }

            wrapper.setCancelled(false);
            return true;
        }

        return false;
    }

    public void clearItems() {
        for (int i = 0; i < this.items.length; i++) {
            this.items[i] = BedrockItem.empty();
        }
    }

    public Item getJavaItem(final int slot) {
        return this.user.get(ItemRewriter.class).javaItem(this.getItem(slot));
    }

    public Item[] getJavaItems() {
        return this.user.get(ItemRewriter.class).javaItems(this.items);
    }

    public BedrockItem getItem(final int slot) {
        return this.items[slot];
    }

    public BedrockItem[] getItems() {
        return Arrays.copyOf(this.items, this.items.length);
    }

    public boolean setItem(final int slot, final BedrockItem item) {
        if (slot < 0 || slot >= this.items.length) {
            ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Tried to set item for " + this.type + ", but slot was out of bounds (" + slot + ")");
            return false;
        }

        final BedrockItem oldItem = this.items[slot];
        this.items[slot] = item;
        this.onSlotChanged(slot, oldItem, item);
        return true;
    }

    public boolean setItems(final BedrockItem[] items) {
        if (items.length != this.items.length) {
            ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Tried to set items for " + this.type + ", but items array length was not correct (" + items.length + " != " + this.items.length + ")");
            return false;
        }

        for (int i = 0; i < items.length; i++) {
            this.setItem(i, items[i]);
        }
        return true;
    }

    public int javaSlot(final int slot) {
        return slot;
    }

    public int bedrockSlot(final int slot) {
        return slot;
    }

    public byte javaContainerId() {
        return this.containerId();
    }

    public int size() {
        return this.items.length;
    }

    public byte containerId() {
        return this.containerId;
    }

    public ContainerType type() {
        return this.type;
    }

    public TextComponent title() {
        return this.title;
    }

    public BlockPosition position() {
        return this.position;
    }

    public Entity attachedEntity() {
        return attachedEntity;
    }

    public boolean isValidBlockTag(final String tag) {
        if (tag == null) {
            return false;
        } else {
            return this.validBlockTags.contains(tag);
        }
    }

    public boolean isValidEntity() {
        return this.attachedEntity.entityData().containsKey(ActorDataIDs.CONTAINER_SIZE);
    }

    protected void onSlotChanged(final int slot, final BedrockItem oldItem, final BedrockItem newItem) {
    }

}
