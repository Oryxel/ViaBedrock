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
package net.raphimc.viabedrock.protocol.packet;

import com.viaversion.viaversion.api.minecraft.BlockPosition;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ClientboundBedrockPackets;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.SoundEvent;
import net.raphimc.viabedrock.protocol.model.Position3f;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

public class SoundPackets {

    public static void register(final BedrockProtocol protocol) {
        protocol.registerClientbound(ClientboundBedrockPackets.PLAY_SOUND, null, wrapper -> {
            wrapper.cancel();
            final EntityTracker entityTracker = wrapper.user().get(EntityTracker.class);

            String bedrockSound = wrapper.read(BedrockTypes.STRING);
            BlockPosition position = wrapper.read(BedrockTypes.BLOCK_POSITION);
            float volume = wrapper.read(BedrockTypes.FLOAT_LE);
            float pitch = wrapper.read(BedrockTypes.FLOAT_LE);

            entityTracker.getClientPlayer().playSound(bedrockSound, position, volume, pitch);
        });

        protocol.registerClientbound(ClientboundBedrockPackets.LEVEL_SOUND_EVENT, null, wrapper -> {
            wrapper.cancel();
            final EntityTracker entityTracker = wrapper.user().get(EntityTracker.class);

            System.out.println("play sound!");

            SoundEvent soundEvent = SoundEvent.values()[wrapper.read(BedrockTypes.UNSIGNED_VAR_INT)];
            Position3f position = wrapper.read(BedrockTypes.POSITION_3F);
            int extraData = wrapper.read(BedrockTypes.VAR_INT);
            String sourceIdentifier = wrapper.read(BedrockTypes.STRING).replace("minecraft:", "");

            entityTracker.getClientPlayer().playLevelSound(soundEvent, position, sourceIdentifier);
        });

        protocol.registerClientbound(ClientboundBedrockPackets.LEVEL_SOUND_EVENT_V1, null, wrapper -> {
            wrapper.cancel();
            final EntityTracker entityTracker = wrapper.user().get(EntityTracker.class);

            System.out.println("play sound!");

            SoundEvent soundEvent = SoundEvent.values()[wrapper.read(BedrockTypes.UNSIGNED_VAR_INT)];
            Position3f position = wrapper.read(BedrockTypes.POSITION_3F);
            int extraData = wrapper.read(BedrockTypes.VAR_INT);
            String sourceIdentifier = wrapper.read(BedrockTypes.STRING).replace("minecraft:", "");

            entityTracker.getClientPlayer().playLevelSound(soundEvent, position, sourceIdentifier);
        });

        protocol.registerClientbound(ClientboundBedrockPackets.LEVEL_SOUND_EVENT_V2, null, wrapper -> {
            wrapper.cancel();
            final EntityTracker entityTracker = wrapper.user().get(EntityTracker.class);

            System.out.println("play sound!");

            SoundEvent soundEvent = SoundEvent.values()[wrapper.read(BedrockTypes.UNSIGNED_VAR_INT)];
            Position3f position = wrapper.read(BedrockTypes.POSITION_3F);
            int extraData = wrapper.read(BedrockTypes.VAR_INT);
            String sourceIdentifier = wrapper.read(BedrockTypes.STRING).replace("minecraft:", "");

            entityTracker.getClientPlayer().playLevelSound(soundEvent, position, sourceIdentifier);
        });
    }

}
