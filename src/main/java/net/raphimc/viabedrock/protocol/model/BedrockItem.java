/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * Copyright (C) 2023-2024 RK_01/RaphiMC and contributors
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
package net.raphimc.viabedrock.protocol.model;

import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.libs.opennbt.tag.builtin.CompoundTag;

import java.util.Arrays;
import java.util.Objects;

public class BedrockItem implements Item {

    private int id;
    private short data;
    private byte amount;
    private CompoundTag tag;
    private String[] canPlace;
    private String[] canBreak;
    private long blockingTicks;
    private int blockRuntimeId;
    private boolean usingNetId;
    private int netId;

    public BedrockItem(final int id) {
        this(id, (short) 0, (byte) 1);
    }

    public BedrockItem(final int id, final short data, final byte amount) {
        this(id, data, amount, null);
    }

    public BedrockItem(final int id, final short data, final byte amount, final CompoundTag tag) {
        this(id, data, amount, tag, new String[0], new String[0], 0, 0, false, 0);
    }

    public BedrockItem(final int id, final short data, final byte amount, final CompoundTag tag, final String[] canPlace, final String[] canBreak, final long blockingTicks, final int blockRuntimeId, final boolean usingNetId, final int netId) {
        this.id = id;
        this.data = data;
        this.amount = amount;
        this.tag = tag;
        this.canPlace = canPlace;
        this.canBreak = canBreak;
        this.blockingTicks = blockingTicks;
        this.blockRuntimeId = blockRuntimeId;
        this.usingNetId = usingNetId;
        this.netId = netId;
    }

    @Override
    public int identifier() {
        return this.id;
    }

    @Override
    public void setIdentifier(final int identifier) {
        this.id = identifier % 65536;
    }

    @Override
    public short data() {
        return this.data;
    }

    @Override
    public void setData(final short data) {
        if (data < 0) {
            this.data = 0;
        } else {
            this.data = data;
        }
    }

    public void setData(final int data) {
        this.setData((short) (data % 65536));
    }

    @Override
    public int amount() {
        return this.amount & 0xFF;
    }

    @Override
    public void setAmount(final int amount) {
        if (amount < 0 || amount > 255) {
            this.amount = 0;
        } else {
            this.amount = (byte) amount;
        }
    }

    @Override
    public CompoundTag tag() {
        return this.tag;
    }

    @Override
    public void setTag(final CompoundTag tag) {
        this.tag = tag;
    }

    public String[] canPlace() {
        return this.canPlace;
    }

    public void setCanPlace(final String[] canPlace) {
        this.canPlace = canPlace;
    }

    public String[] canBreak() {
        return this.canBreak;
    }

    public void setCanBreak(final String[] canBreak) {
        this.canBreak = canBreak;
    }

    public long blockingTicks() {
        return this.blockingTicks;
    }

    public void setBlockingTicks(final long blockingTicks) {
        this.blockingTicks = blockingTicks;
    }

    public int blockRuntimeId() {
        return this.blockRuntimeId;
    }

    public void setBlockRuntimeId(final int blockRuntimeId) {
        this.blockRuntimeId = blockRuntimeId;
    }

    public boolean usingNetId() {
        return this.usingNetId;
    }

    public void setUsingNetId(final boolean usingNetId) {
        this.usingNetId = usingNetId;
    }

    public int netId() {
        return this.netId;
    }

    public void setNetId(final int netId) {
        this.netId = netId;
    }

    @Override
    public BedrockItem copy() {
        return new BedrockItem(this.id, this.data, this.amount, this.tag != null ? this.tag.clone() : null, this.canPlace.clone(), this.canBreak.clone(), this.blockingTicks, this.blockRuntimeId, this.usingNetId, this.netId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BedrockItem that = (BedrockItem) o;
        return id == that.id && data == that.data && amount == that.amount && blockingTicks == that.blockingTicks && blockRuntimeId == that.blockRuntimeId && usingNetId == that.usingNetId && netId == that.netId && Objects.equals(tag, that.tag) && Arrays.equals(canPlace, that.canPlace) && Arrays.equals(canBreak, that.canBreak);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, data, amount, tag, blockingTicks, blockRuntimeId, usingNetId, netId);
        result = 31 * result + Arrays.hashCode(canPlace);
        result = 31 * result + Arrays.hashCode(canBreak);
        return result;
    }

    @Override
    public String toString() {
        return "BedrockItem{" +
                "id=" + id +
                ", data=" + data +
                ", amount=" + amount +
                ", tag=" + tag +
                ", canPlace=" + Arrays.toString(canPlace) +
                ", canBreak=" + Arrays.toString(canBreak) +
                ", blockingTicks=" + blockingTicks +
                ", blockRuntimeId=" + blockRuntimeId +
                ", usingNetId=" + usingNetId +
                ", netId=" + netId +
                '}';
    }

}
