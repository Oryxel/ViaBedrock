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
package net.raphimc.viabedrock.protocol.packets;

import com.viaversion.viaversion.api.minecraft.entities.EntityTypes1_20_5;
import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.libs.fastutil.ints.Int2IntMap;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.packet.ClientboundPackets1_20_5;
import net.raphimc.viabedrock.ViaBedrock;
import net.raphimc.viabedrock.api.model.entity.ClientPlayerEntity;
import net.raphimc.viabedrock.api.model.entity.Entity;
import net.raphimc.viabedrock.api.model.entity.PlayerEntity;
import net.raphimc.viabedrock.api.util.BitSets;
import net.raphimc.viabedrock.api.util.MathUtil;
import net.raphimc.viabedrock.api.util.StringUtil;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ClientboundBedrockPackets;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.PlayerPositionModeComponent_PositionMode;
import net.raphimc.viabedrock.protocol.data.enums.java.PlayerInfoUpdateAction;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.EntityLink;
import net.raphimc.viabedrock.protocol.model.PlayerAbilities;
import net.raphimc.viabedrock.protocol.model.Position3f;
import net.raphimc.viabedrock.protocol.rewriter.GameTypeRewriter;
import net.raphimc.viabedrock.protocol.rewriter.ItemRewriter;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;
import net.raphimc.viabedrock.protocol.storage.GameSessionStorage;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class OtherPlayerPackets {

    public static void register(final BedrockProtocol protocol) {
        protocol.registerClientbound(ClientboundBedrockPackets.ADD_PLAYER, ClientboundPackets1_20_5.ADD_ENTITY, wrapper -> {
            final GameSessionStorage gameSession = wrapper.user().get(GameSessionStorage.class);
            final ItemRewriter itemRewriter = wrapper.user().get(ItemRewriter.class);
            final EntityTracker entityTracker = wrapper.user().get(EntityTracker.class);

            final UUID uuid = wrapper.read(BedrockTypes.UUID); // uuid
            final String username = wrapper.read(BedrockTypes.STRING); // username
            final long runtimeEntityId = wrapper.read(BedrockTypes.UNSIGNED_VAR_LONG); // runtime entity id
            final String platformChatId = wrapper.read(BedrockTypes.STRING); // platform chat id
            final Position3f position = wrapper.read(BedrockTypes.POSITION_3F); // position
            final Position3f motion = wrapper.read(BedrockTypes.POSITION_3F); // motion
            final Position3f rotation = wrapper.read(BedrockTypes.POSITION_3F); // rotation
            final BedrockItem item = wrapper.read(itemRewriter.itemType()); // hand item
            final int gameType = wrapper.read(BedrockTypes.VAR_INT); // game type
            final EntityData[] metadata = wrapper.read(BedrockTypes.METADATA_ARRAY); // metadata
            final Int2IntMap intProperties = wrapper.read(BedrockTypes.INT_PROPERTIES); // int properties
            final Map<Integer, Float> floatProperties = wrapper.read(BedrockTypes.FLOAT_PROPERTIES); // float properties
            final PlayerAbilities abilities = wrapper.read(BedrockTypes.PLAYER_ABILITIES); // abilities
            final EntityLink[] entityLinks = wrapper.read(BedrockTypes.ENTITY_LINK_ARRAY); // entity links

            // TODO: Handle remaining fields

            final PlayerEntity entity = (PlayerEntity) entityTracker.addEntity(abilities.uniqueEntityId(), runtimeEntityId, uuid, EntityTypes1_20_5.PLAYER);
            entity.setPosition(position);
            entity.setRotation(rotation);
            entity.updateName(username);

            final PacketWrapper playerInfoUpdate = PacketWrapper.create(ClientboundPackets1_20_5.PLAYER_INFO_UPDATE, wrapper.user());
            playerInfoUpdate.write(Types.PROFILE_ACTIONS_ENUM, BitSets.create(6, PlayerInfoUpdateAction.ADD_PLAYER.ordinal(), PlayerInfoUpdateAction.UPDATE_GAME_MODE.ordinal())); // actions
            playerInfoUpdate.write(Types.VAR_INT, 1); // length
            playerInfoUpdate.write(Types.UUID, uuid); // uuid
            playerInfoUpdate.write(Types.STRING, StringUtil.encodeUUID(uuid)); // username
            playerInfoUpdate.write(Types.VAR_INT, 3); // property count
            playerInfoUpdate.write(Types.STRING, "platform_chat_id"); // property name
            playerInfoUpdate.write(Types.STRING, platformChatId); // property value
            playerInfoUpdate.write(Types.OPTIONAL_STRING, null); // signature
            playerInfoUpdate.write(Types.STRING, "device_id"); // property name
            playerInfoUpdate.write(Types.STRING, wrapper.read(BedrockTypes.STRING)); // device id
            playerInfoUpdate.write(Types.OPTIONAL_STRING, null); // signature
            playerInfoUpdate.write(Types.STRING, "device_os"); // property name
            playerInfoUpdate.write(Types.STRING, wrapper.read(BedrockTypes.INT_LE).toString()); // device os
            playerInfoUpdate.write(Types.OPTIONAL_STRING, null); // signature
            playerInfoUpdate.write(Types.VAR_INT, (int) GameTypeRewriter.getEffectiveGameMode(gameType, gameSession.getLevelGameType())); // game mode
            playerInfoUpdate.send(BedrockProtocol.class);

            wrapper.write(Types.VAR_INT, entity.javaId()); // entity id
            wrapper.write(Types.UUID, uuid); // uuid
            wrapper.write(Types.VAR_INT, EntityTypes1_20_5.PLAYER.getId()); // type id
            wrapper.write(Types.DOUBLE, (double) position.x()); // x
            wrapper.write(Types.DOUBLE, (double) position.y()); // y
            wrapper.write(Types.DOUBLE, (double) position.z()); // z
            wrapper.write(Types.BYTE, MathUtil.float2Byte(rotation.x())); // pitch
            wrapper.write(Types.BYTE, MathUtil.float2Byte(rotation.y())); // yaw
            wrapper.write(Types.BYTE, MathUtil.float2Byte(rotation.z())); // head yaw
            wrapper.write(Types.VAR_INT, 0); // data
            wrapper.write(Types.SHORT, (short) (motion.x() * 8000F)); // velocity x
            wrapper.write(Types.SHORT, (short) (motion.y() * 8000F)); // velocity y
            wrapper.write(Types.SHORT, (short) (motion.z() * 8000F)); // velocity z
        });
        protocol.registerClientbound(ClientboundBedrockPackets.MOVE_PLAYER, ClientboundPackets1_20_5.TELEPORT_ENTITY, wrapper -> {
            final EntityTracker entityTracker = wrapper.user().get(EntityTracker.class);

            final long runtimeEntityId = wrapper.read(BedrockTypes.UNSIGNED_VAR_LONG); // runtime entity id
            final Position3f position = wrapper.read(BedrockTypes.POSITION_3F); // position
            final Position3f rotation = wrapper.read(BedrockTypes.POSITION_3F); // rotation
            final PlayerPositionModeComponent_PositionMode mode = PlayerPositionModeComponent_PositionMode.getByValue(wrapper.read(Types.UNSIGNED_BYTE), PlayerPositionModeComponent_PositionMode.OnlyHeadRot); // mode
            final boolean onGround = wrapper.read(Types.BOOLEAN); // on ground
            wrapper.read(BedrockTypes.UNSIGNED_VAR_LONG); // riding runtime entity id
            if (mode == PlayerPositionModeComponent_PositionMode.Teleport) {
                wrapper.read(BedrockTypes.INT_LE); // teleportation cause
                wrapper.read(BedrockTypes.INT_LE); // entity type
            }
            wrapper.read(BedrockTypes.UNSIGNED_VAR_LONG); // tick

            final Entity entity = entityTracker.getEntityByRid(runtimeEntityId);
            if (entity == null) {
                wrapper.cancel();
                return;
            }
            if (!entity.type().isOrHasParent(EntityTypes1_20_5.PLAYER)) {
                ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Received move player packet for non-player entity: " + entity.type());
                wrapper.cancel();
                return;
            }

            if (mode == PlayerPositionModeComponent_PositionMode.OnlyHeadRot) {
                BedrockProtocol.kickForIllegalState(wrapper.user(), "PlayerPositionModeComponent_PositionMode.OnlyHeadRot is not implemented");
                return;
            }

            entity.setPosition(position);
            entity.setRotation(rotation);
            entity.setOnGround(onGround);

            if ((mode == PlayerPositionModeComponent_PositionMode.Teleport || mode == PlayerPositionModeComponent_PositionMode.Respawn) && entity instanceof ClientPlayerEntity clientPlayer) {
                wrapper.setPacketType(ClientboundPackets1_20_5.PLAYER_POSITION);
                clientPlayer.writePlayerPositionPacketToClient(wrapper, false, mode == PlayerPositionModeComponent_PositionMode.Respawn);
                return;
            }

            wrapper.write(Types.VAR_INT, entity.javaId()); // entity id
            wrapper.write(Types.DOUBLE, (double) position.x()); // x
            wrapper.write(Types.DOUBLE, (double) position.y() - entity.eyeOffset()); // y
            wrapper.write(Types.DOUBLE, (double) position.z()); // z
            wrapper.write(Types.BYTE, MathUtil.float2Byte(rotation.y())); // yaw
            wrapper.write(Types.BYTE, MathUtil.float2Byte(rotation.x())); // pitch
            wrapper.write(Types.BOOLEAN, onGround); // on ground

            final PacketWrapper entityHeadLook = PacketWrapper.create(ClientboundPackets1_20_5.ROTATE_HEAD, wrapper.user());
            entityHeadLook.write(Types.VAR_INT, entity.javaId()); // entity id
            entityHeadLook.write(Types.BYTE, MathUtil.float2Byte(rotation.z())); // head yaw
            entityHeadLook.send(BedrockProtocol.class);
        });
    }

}
