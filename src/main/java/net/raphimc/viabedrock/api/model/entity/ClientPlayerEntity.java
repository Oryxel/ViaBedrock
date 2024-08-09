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
package net.raphimc.viabedrock.api.model.entity;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_20_5to1_21.packet.ClientboundPackets1_21;
import com.viaversion.viaversion.util.Pair;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.raphimc.viabedrock.ViaBedrock;
import net.raphimc.viabedrock.api.util.MathUtil;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ServerboundBedrockPackets;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.*;
import net.raphimc.viabedrock.protocol.data.enums.java.AbilitiesFlag;
import net.raphimc.viabedrock.protocol.data.enums.java.GameMode;
import net.raphimc.viabedrock.protocol.model.*;
import net.raphimc.viabedrock.protocol.rewriter.GameTypeRewriter;
import net.raphimc.viabedrock.protocol.rewriter.ItemRewriter;
import net.raphimc.viabedrock.protocol.storage.*;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import net.raphimc.viabedrock.protocol.types.item.BedrockItemType;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class ClientPlayerEntity extends PlayerEntity {

    private final AtomicInteger TELEPORT_ID = new AtomicInteger(1);
    private final GameSessionStorage gameSession;

    // Initial spawn and respawning
    private boolean initiallySpawned;
    private boolean changingDimension;
    private boolean wasInsideUnloadedChunk;

    // Position syncing
    private int pendingTeleportId;
    private boolean waitingForPositionSync;

    // Server Authoritative Movement
    private Position3f prevPosition;
    private final EnumSet<PlayerAuthInputPacket_InputData> authInputData = EnumSet.noneOf(PlayerAuthInputPacket_InputData.class);
    private boolean sneaking;
    private boolean sprinting;

    // Misc data
    private GameType gameType;
    private GameMode javaGameMode;

    public ClientPlayerEntity(final UserConnection user, final long runtimeId, final UUID javaUuid, final PlayerAbilities abilities) {
        super(user, runtimeId, 0, javaUuid, abilities);
        this.attributes.put("minecraft:movement", new EntityAttribute("minecraft:movement", 0.7F, 0F, Float.MAX_VALUE));
        this.attributes.put("minecraft:player.hunger", new EntityAttribute("minecraft:player.hunger", 20F, 0F, 20F));
        this.attributes.put("minecraft:player.saturation", new EntityAttribute("minecraft:player.saturation", 5F, 0F, 20F));
        this.attributes.put("minecraft:player.experience", new EntityAttribute("minecraft:player.experience", 0F, 0F, 1F));
        this.attributes.put("minecraft:player.level", new EntityAttribute("minecraft:player.level", 0F, 0F, 24791F));

        this.gameSession = user.get(GameSessionStorage.class);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.gameSession.getMovementMode() != ServerAuthMovementMode.ClientAuthoritative && this.initiallySpawned && !this.isDead()) {
            this.sendPlayerAuthInputPacketToServer(ClientPlayMode.Screen);
        }
    }

    public void sendInteractPacketToServer(long runtimeId, int type) {
        final InventoryTracker tracker = this.user.get(InventoryTracker.class);

        int selectedSlot = tracker.getInventoryContainer().selectedHotbarSlot;
        final BedrockItem item = tracker.getInventoryContainer().getItem(selectedSlot);

        BedrockItemType itemType = (BedrockItemType) this.user.get(ItemRewriter.class).getItemType();

        System.out.println(type);
        ByteBuf buffer = Unpooled.buffer();
        try {
            BedrockTypes.VAR_INT.write(buffer, 1); // legacy request id
            BedrockTypes.UNSIGNED_VAR_INT.write(buffer, 3); // transaction type
            BedrockTypes.UNSIGNED_VAR_LONG.write(buffer, 0L);
            BedrockTypes.UNSIGNED_VAR_LONG.write(buffer, runtimeId); // runtime id
            BedrockTypes.UNSIGNED_VAR_INT.write(buffer, type); // action type
            BedrockTypes.UNSIGNED_VAR_INT.write(buffer, selectedSlot); // selected slot
            itemType.write(buffer, item); // item
            BedrockTypes.POSITION_3F.write(buffer, position); // player position
            BedrockTypes.POSITION_3F.write(buffer, new Position3f(0, 0, 0)); // click position (for touch user?)

            final PacketWrapper inventoryTransaction = PacketWrapper.create(ServerboundBedrockPackets.INVENTORY_TRANSACTION, buffer, this.user);
            inventoryTransaction.sendToServer(BedrockProtocol.class);
        } finally {
            buffer.release();
        }
    }

    public void sendPlayerPositionPacketToClient(final boolean keepRotation) {
        final PacketWrapper playerPosition = PacketWrapper.create(ClientboundPackets1_21.PLAYER_POSITION, this.user);
        this.writePlayerPositionPacketToClient(playerPosition, keepRotation, true);
        playerPosition.send(BedrockProtocol.class);
    }

    public void writePlayerPositionPacketToClient(final PacketWrapper wrapper, final boolean keepRotation, final boolean fakeTeleport) {
        this.pendingTeleportId = TELEPORT_ID.getAndIncrement();

        wrapper.write(Types.DOUBLE, (double) this.position.x()); // x
        wrapper.write(Types.DOUBLE, (double) this.position.y() - this.eyeOffset()); // y
        wrapper.write(Types.DOUBLE, (double) this.position.z()); // z
        wrapper.write(Types.FLOAT, keepRotation ? 0F : this.rotation.y()); // yaw
        wrapper.write(Types.FLOAT, keepRotation ? 0F : this.rotation.x()); // pitch
        wrapper.write(Types.BYTE, (byte) (keepRotation ? 0b11000 : 0)); // flags
        wrapper.write(Types.VAR_INT, this.pendingTeleportId * (fakeTeleport ? -1 : 1)); // teleport id
    }

    public void sendMovePlayerPacketToServer(final PlayerPositionModeComponent_PositionMode mode) {
        final PacketWrapper movePlayer = PacketWrapper.create(ServerboundBedrockPackets.MOVE_PLAYER, this.user);
        this.writeMovePlayerPacketToServer(movePlayer, mode);
        movePlayer.sendToServer(BedrockProtocol.class);
    }

    public void writeMovePlayerPacketToServer(final PacketWrapper wrapper, final PlayerPositionModeComponent_PositionMode mode) {
        wrapper.write(BedrockTypes.UNSIGNED_VAR_LONG, this.runtimeId); // runtime entity id
        wrapper.write(BedrockTypes.POSITION_3F, this.position); // position
        wrapper.write(BedrockTypes.POSITION_3F, this.rotation); // rotation
        wrapper.write(Types.BYTE, (byte) mode.getValue()); // mode
        wrapper.write(Types.BOOLEAN, this.onGround); // on ground
        wrapper.write(BedrockTypes.UNSIGNED_VAR_LONG, 0L); // riding runtime entity id
        wrapper.write(BedrockTypes.UNSIGNED_VAR_LONG, 0L); // tick
    }

    public void sendPlayerAuthInputPacketToServer(final ClientPlayMode playMode) {
        if (this.prevPosition == null) this.prevPosition = this.position;
        final Position3f positionDelta = this.position.subtract(this.prevPosition);
        final Position3f gravityAffectedPositionDelta;
        if (this.abilities.getBooleanValue(AbilitiesIndex.Flying)) {
            gravityAffectedPositionDelta = positionDelta;
        } else {
            gravityAffectedPositionDelta = positionDelta.subtract(0F, ProtocolConstants.PLAYER_GRAVITY, 0F);
        }

        this.authInputData.add(PlayerAuthInputPacket_InputData.BlockBreakingDelayEnabled);
        if (this.sneaking) {
            this.addAuthInputData(PlayerAuthInputPacket_InputData.SneakDown, PlayerAuthInputPacket_InputData.Sneaking, PlayerAuthInputPacket_InputData.WantDown);
        }
        if (this.sprinting) {
            this.addAuthInputData(PlayerAuthInputPacket_InputData.SprintDown, PlayerAuthInputPacket_InputData.Sprinting);
        }
        if (MathUtil.roughlyEquals(positionDelta.y(), ProtocolConstants.PLAYER_JUMP_HEIGHT)) {
            this.authInputData.add(PlayerAuthInputPacket_InputData.StartJumping);
        }
        if (positionDelta.y() > 0F) {
            this.addAuthInputData(PlayerAuthInputPacket_InputData.JumpDown, PlayerAuthInputPacket_InputData.Jumping, PlayerAuthInputPacket_InputData.WantUp);
        }
        this.authInputData.addAll(MathUtil.calculatePressedDirectionKeys(positionDelta, this.rotation.y()));

        final float[] movementDirections = MathUtil.calculateMovementDirections(this.authInputData, this.sneaking);

        final PacketWrapper playerAuthInput = PacketWrapper.create(ServerboundBedrockPackets.PLAYER_AUTH_INPUT, this.user);
        playerAuthInput.write(BedrockTypes.FLOAT_LE, this.rotation.x()); // pitch
        playerAuthInput.write(BedrockTypes.FLOAT_LE, this.rotation.y()); // yaw
        playerAuthInput.write(BedrockTypes.POSITION_3F, this.position); // position
        playerAuthInput.write(BedrockTypes.POSITION_2F, new Position2f(movementDirections[0], movementDirections[1])); // motion
        playerAuthInput.write(BedrockTypes.FLOAT_LE, this.rotation.z()); // head yaw
        playerAuthInput.write(BedrockTypes.UNSIGNED_VAR_LONG, this.authInputData.stream().mapToLong(d -> 1L << d.getValue()).reduce(0L, (l1, l2) -> l1 | l2)); // input flags
        playerAuthInput.write(BedrockTypes.UNSIGNED_VAR_INT, InputMode.Mouse.getValue()); // input mode
        playerAuthInput.write(BedrockTypes.UNSIGNED_VAR_INT, playMode.getValue()); // play mode
        playerAuthInput.write(BedrockTypes.UNSIGNED_VAR_INT, NewInteractionModel.Touch.getValue()); // interaction mode
        playerAuthInput.write(BedrockTypes.UNSIGNED_VAR_LONG, (long) this.age); // tick
        playerAuthInput.write(BedrockTypes.POSITION_3F, gravityAffectedPositionDelta); // position delta
        playerAuthInput.write(BedrockTypes.POSITION_2F, new Position2f(0F, 0F)); // analog move vector
        playerAuthInput.sendToServer(BedrockProtocol.class);

        this.prevPosition = this.position;
        this.authInputData.clear();
    }

    public void sendPlayerActionPacketToServer(final PlayerActionType action, final int face) {
        final PacketWrapper playerAction = PacketWrapper.create(ServerboundBedrockPackets.PLAYER_ACTION, this.user);
        playerAction.write(BedrockTypes.UNSIGNED_VAR_LONG, this.runtimeId); // runtime entity id
        playerAction.write(BedrockTypes.VAR_INT, action.getValue()); // action
        playerAction.write(BedrockTypes.BLOCK_POSITION, new BlockPosition(0, 0, 0)); // block position
        playerAction.write(BedrockTypes.BLOCK_POSITION, new BlockPosition(0, 0, 0)); // result position
        playerAction.write(BedrockTypes.VAR_INT, face); // face
        playerAction.sendToServer(BedrockProtocol.class);
    }

    public void updatePlayerPosition(final PacketWrapper wrapper, final boolean onGround) {
        if (!this.preMove(null, null, onGround)) {
            wrapper.cancel();
            return;
        }

        this.onGround = onGround;

        if (this.gameSession.getMovementMode() == ServerAuthMovementMode.ClientAuthoritative) {
            this.writeMovePlayerPacketToServer(wrapper, PlayerPositionModeComponent_PositionMode.Normal);
        } else {
            wrapper.cancel();
        }
    }

    public void updatePlayerPosition(final PacketWrapper wrapper, final double x, final double y, final double z, final boolean onGround) {
        final Position3f newPosition = new Position3f((float) x, (float) y + this.eyeOffset(), (float) z);

        if (!this.preMove(newPosition, null, onGround)) {
            wrapper.cancel();
            return;
        }

        if (this.gameSession.getMovementMode() == ServerAuthMovementMode.ClientAuthoritative && MathUtil.roughlyEquals(newPosition.y() - this.position.y(), ProtocolConstants.PLAYER_JUMP_HEIGHT)) {
            this.sendPlayerActionPacketToServer(PlayerActionType.StartJump, 0);
        }

        this.position = newPosition;
        this.onGround = onGround;

        if (this.gameSession.getMovementMode() == ServerAuthMovementMode.ClientAuthoritative) {
            this.writeMovePlayerPacketToServer(wrapper, PlayerPositionModeComponent_PositionMode.Normal);
        } else {
            wrapper.cancel();
        }
    }

    public void updatePlayerPosition(final PacketWrapper wrapper, final double x, final double y, final double z, final float yaw, final float pitch, final boolean onGround) {
        final Position3f newPosition = new Position3f((float) x, (float) y + this.eyeOffset(), (float) z);
        final Position3f newRotation = new Position3f(pitch, yaw, yaw);

        if (!this.preMove(newPosition, newRotation, onGround)) {
            wrapper.cancel();
            return;
        }

        if (this.gameSession.getMovementMode() == ServerAuthMovementMode.ClientAuthoritative && MathUtil.roughlyEquals(newPosition.y() - this.position.y(), ProtocolConstants.PLAYER_JUMP_HEIGHT)) {
            this.sendPlayerActionPacketToServer(PlayerActionType.StartJump, 0);
        }

        this.position = newPosition;
        this.rotation = newRotation;
        this.onGround = onGround;

        if (this.gameSession.getMovementMode() == ServerAuthMovementMode.ClientAuthoritative) {
            this.writeMovePlayerPacketToServer(wrapper, PlayerPositionModeComponent_PositionMode.Normal);
        } else {
            wrapper.cancel();
        }
    }

    public void updatePlayerPosition(final PacketWrapper wrapper, final float yaw, final float pitch, final boolean onGround) {
        final Position3f newRotation = new Position3f(pitch, yaw, yaw);

        if (!this.preMove(null, newRotation, onGround)) {
            wrapper.cancel();
            return;
        }

        this.rotation = newRotation;
        this.onGround = onGround;

        if (this.gameSession.getMovementMode() == ServerAuthMovementMode.ClientAuthoritative) {
            this.writeMovePlayerPacketToServer(wrapper, PlayerPositionModeComponent_PositionMode.Normal);
        } else {
            wrapper.cancel();
        }
    }

    public void confirmTeleport(final int teleportId) {
        if (teleportId < 0) { // Fake teleport
            if (this.pendingTeleportId == -teleportId) {
                this.pendingTeleportId = 0;
            }
        } else {
            if (!this.initiallySpawned) {
                ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Received teleport confirm for teleport id " + teleportId + " but player is not spawned yet");
            }
            if (this.gameSession.getMovementMode() == ServerAuthMovementMode.ClientAuthoritative) {
                this.sendPlayerActionPacketToServer(PlayerActionType.HandledTeleport, 0);
            } else {
                this.authInputData.add(PlayerAuthInputPacket_InputData.HandledTeleport);
            }
        }
    }

    public void addAuthInputData(final PlayerAuthInputPacket_InputData data) {
        this.authInputData.add(data);
    }

    public void addAuthInputData(final PlayerAuthInputPacket_InputData... data) {
        this.authInputData.addAll(Arrays.asList(data));
    }

    @Override
    public void setPosition(final Position3f position) {
        this.prevPosition = null;
        super.setPosition(position);
    }

    @Override
    public String name() {
        final PlayerListStorage playerList = this.user.get(PlayerListStorage.class);
        final Pair<Long, String> entry = playerList.getPlayer(this.javaUuid);
        if (entry != null) {
            return entry.value();
        }

        return this.name;
    }

    @Override
    public void setAbilities(final PlayerAbilities abilities) {
        final PacketWrapper playerAbilities = PacketWrapper.create(ClientboundPackets1_21.PLAYER_ABILITIES, this.user);
        this.setAbilities(abilities, playerAbilities);
        playerAbilities.send(BedrockProtocol.class);
    }

    public void setAbilities(final PlayerAbilities abilities, final PacketWrapper javaAbilities) {
        final PlayerAbilities prevAbilities = this.abilities;
        super.setAbilities(abilities);
        if (abilities.commandPermission() != prevAbilities.commandPermission()) {
            final CommandsStorage commandsStorage = this.user.get(CommandsStorage.class);
            if (commandsStorage != null) {
                commandsStorage.updateCommandTree();
            }
        }

        byte flags = 0;
        if (abilities.getBooleanValue(AbilitiesIndex.Invulnerable)) flags |= AbilitiesFlag.INVULNERABLE.getBit();
        if (abilities.getBooleanValue(AbilitiesIndex.Flying)) flags |= AbilitiesFlag.FLYING.getBit();
        if (abilities.getBooleanValue(AbilitiesIndex.MayFly)) flags |= AbilitiesFlag.CAN_FLY.getBit();
        if (abilities.getBooleanValue(AbilitiesIndex.Instabuild)) flags |= AbilitiesFlag.INSTABUILD.getBit();
        javaAbilities.write(Types.BYTE, flags); // flags
        javaAbilities.write(Types.FLOAT, abilities.getFloatValue(AbilitiesIndex.FlySpeed)); // fly speed
        javaAbilities.write(Types.FLOAT, abilities.getFloatValue(AbilitiesIndex.WalkSpeed)); // walk speed
    }

    public boolean isInitiallySpawned() {
        return this.initiallySpawned;
    }

    public void setInitiallySpawned() {
        this.initiallySpawned = true;
    }

    public boolean isChangingDimension() {
        return this.changingDimension;
    }

    public void setChangingDimension(final boolean changingDimension) {
        this.changingDimension = changingDimension;
    }

    public boolean isSneaking() {
        return this.sneaking;
    }

    public void setSneaking(final boolean sneaking) {
        this.sneaking = sneaking;
    }

    public boolean isSprinting() {
        return this.sprinting;
    }

    public void setSprinting(final boolean sprinting) {
        this.sprinting = sprinting;
    }

    public GameType gameType() {
        return this.gameType;
    }

    public void setGameType(final GameType gameType) {
        this.gameType = gameType;
        this.updateJavaGameMode();
    }

    public GameMode javaGameMode() {
        return this.javaGameMode;
    }

    public void updateJavaGameMode() {
        this.javaGameMode = GameTypeRewriter.getEffectiveGameMode(this.gameType, this.gameSession.getLevelGameType());

        final PlayerAbilities.AbilitiesLayer abilitiesLayer = this.abilities.getOrCreateCacheLayer();
        switch (this.javaGameMode) {
            case CREATIVE -> {
                abilitiesLayer.setAbility(AbilitiesIndex.Invulnerable, true);
                abilitiesLayer.setAbility(AbilitiesIndex.MayFly, true);
                abilitiesLayer.setAbility(AbilitiesIndex.Instabuild, true);
                abilitiesLayer.setAbility(AbilitiesIndex.NoClip, false);
            }
            case SPECTATOR -> {
                abilitiesLayer.setAbility(AbilitiesIndex.Invulnerable, true);
                abilitiesLayer.setAbility(AbilitiesIndex.Flying, true);
                abilitiesLayer.setAbility(AbilitiesIndex.MayFly, true);
                abilitiesLayer.setAbility(AbilitiesIndex.Instabuild, false);
                abilitiesLayer.setAbility(AbilitiesIndex.NoClip, true);
            }
            default -> {
                abilitiesLayer.setAbility(AbilitiesIndex.Invulnerable, false);
                abilitiesLayer.setAbility(AbilitiesIndex.Flying, false);
                abilitiesLayer.setAbility(AbilitiesIndex.MayFly, false);
                abilitiesLayer.setAbility(AbilitiesIndex.Instabuild, false);
                abilitiesLayer.setAbility(AbilitiesIndex.NoClip, false);
            }
        }
    }

    @Override
    protected boolean translateAttribute(final EntityAttribute attribute, final PacketWrapper javaAttributes, final AtomicInteger attributeCount, final List<EntityData> javaEntityData) {
        return switch (attribute.name()) {
            case "minecraft:health", "minecraft:player.hunger", "minecraft:player.saturation" -> {
                final EntityAttribute health = attribute.name().equals("minecraft:health") ? attribute : this.attributes.get("minecraft:health");
                final EntityAttribute hunger = attribute.name().equals("minecraft:player.hunger") ? attribute : this.attributes.get("minecraft:player.hunger");
                final EntityAttribute saturation = attribute.name().equals("minecraft:player.saturation") ? attribute : this.attributes.get("minecraft:player.saturation");
                final PacketWrapper setHealth = PacketWrapper.create(ClientboundPackets1_21.SET_HEALTH, this.user);
                setHealth.write(Types.FLOAT, health.computeValue(false)); // health
                setHealth.write(Types.VAR_INT, (int) hunger.computeValue(false)); // food
                setHealth.write(Types.FLOAT, saturation.computeValue(false)); // saturation
                setHealth.send(BedrockProtocol.class);

                if (attribute.name().equals("minecraft:health")) { // Call super to translate max health
                    yield super.translateAttribute(attribute, javaAttributes, attributeCount, javaEntityData);
                } else {
                    yield true;
                }
            }
            case "minecraft:player.experience", "minecraft:player.level" -> {
                final EntityAttribute experience = attribute.name().equals("minecraft:player.experience") ? attribute : this.attributes.get("minecraft:player.experience");
                final EntityAttribute level = attribute.name().equals("minecraft:player.level") ? attribute : this.attributes.get("minecraft:player.level");
                final PacketWrapper setExperience = PacketWrapper.create(ClientboundPackets1_21.SET_EXPERIENCE, this.user);
                setExperience.write(Types.FLOAT, experience.computeValue(false)); // bar progress
                setExperience.write(Types.VAR_INT, (int) level.computeValue(false)); // experience level
                setExperience.write(Types.VAR_INT, 0); // total experience
                setExperience.send(BedrockProtocol.class);
                yield true;
            }
            case "minecraft:player.exhaustion" -> true; // Ignore exhaustion
            default -> super.translateAttribute(attribute, javaAttributes, attributeCount, javaEntityData);
        };
    }

    private boolean preMove(final Position3f newPosition, final Position3f newRotation, final boolean newOnGround) {
        final boolean positionLook = newPosition != null && newRotation != null;
        final ChunkTracker chunkTracker = this.user.get(ChunkTracker.class);

        // Waiting for position sync
        if (this.waitingForPositionSync) {
            if (this.pendingTeleportId == 0 && positionLook) {
                this.waitingForPositionSync = false;
            }
            return false;
        }
        // Is in unloaded chunk
        if (chunkTracker.isInUnloadedChunkSection(this.position)) {
            this.wasInsideUnloadedChunk = true;
            if (!this.position.equals(newPosition)) {
                if (!this.initiallySpawned || this.changingDimension) {
                    this.sendPlayerPositionPacketToClient(false);
                } else {
                    this.waitingForPositionSync = true;
                    this.sendPlayerPositionPacketToClient(true);
                }
            }
            return false;
        } else if (this.wasInsideUnloadedChunk) {
            this.wasInsideUnloadedChunk = false;
            this.waitingForPositionSync = true;
            this.sendPlayerPositionPacketToClient(true);
            return false;
        }
        // Loaded -> Unloaded chunk
        if (newPosition != null && chunkTracker.isInUnloadedChunkSection(newPosition)) {
            this.waitingForPositionSync = true;
            this.sendPlayerPositionPacketToClient(true);
            return false;
        }
        // Not spawned yet or respawning
        if (!this.initiallySpawned || this.changingDimension) {
            if (!this.position.equals(newPosition)) {
                this.sendPlayerPositionPacketToClient(false);
            }
            return false;
        }

        if (newPosition != null && !this.position.equals(newPosition)) {
            return true;
        } else if (newRotation != null && !this.rotation.equals(newRotation)) {
            return true;
        } else if (this.onGround != newOnGround) {
            return true;
        }

        return false;
    }

}
