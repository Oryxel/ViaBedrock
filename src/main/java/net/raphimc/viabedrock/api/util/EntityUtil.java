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
package net.raphimc.viabedrock.api.util;

import net.raphimc.viabedrock.protocol.data.enums.bedrock.ActorFlags;

import java.util.EnumSet;

public class EntityUtil {

    // From CloudburstMC Protocol.
    public static EnumSet<ActorFlags> getActorFlags(Long value) {
        EnumSet<ActorFlags> flags = EnumSet.noneOf(ActorFlags.class);

        for (int i = 0; i < 64; i++) {
            int idx = i & 0x3f;
            if ((value & (1L << idx)) != 0) {
                ActorFlags flag = ActorFlags.getByValue(i);
                if (flag == null) continue;
                flags.add(flag);
            }
        }

        return flags;
    }

}
