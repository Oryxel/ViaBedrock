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
package net.raphimc.viabedrock.protocol.providers.impl;

import net.raphimc.viabedrock.protocol.providers.BlobCacheProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryBlobCacheProvider extends BlobCacheProvider {

    private final Map<Long, byte[]> blobs = new ConcurrentHashMap<>();

    @Override
    public byte[] addBlob(final long hash, final byte[] blob) {
        return this.blobs.put(hash, blob);
    }

    @Override
    public boolean hasBlob(final long hash) {
        return this.blobs.containsKey(hash);
    }

    @Override
    public byte[] getBlob(final long hash) {
        return this.blobs.get(hash);
    }

}
