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

import com.viaversion.viaversion.api.minecraft.BlockChangeRecord;
import com.viaversion.viaversion.api.minecraft.BlockChangeRecord1_16_2;
import com.viaversion.viaversion.api.minecraft.Position;
import com.viaversion.viaversion.api.minecraft.blockentity.BlockEntity;
import com.viaversion.viaversion.api.minecraft.chunks.ChunkSection;
import com.viaversion.viaversion.api.minecraft.chunks.PaletteType;
import com.viaversion.viaversion.api.minecraft.metadata.ChunkPosition;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.remapper.PacketHandler;
import com.viaversion.viaversion.api.protocol.remapper.PacketHandlers;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.libs.fastutil.ints.IntObjectPair;
import com.viaversion.viaversion.libs.opennbt.tag.builtin.CompoundTag;
import com.viaversion.viaversion.libs.opennbt.tag.builtin.Tag;
import com.viaversion.viaversion.protocols.protocol1_20_3to1_20_2.packet.ClientboundPackets1_20_3;
import com.viaversion.viaversion.util.MathUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.raphimc.viabedrock.ViaBedrock;
import net.raphimc.viabedrock.api.chunk.BedrockBlockEntity;
import net.raphimc.viabedrock.api.chunk.BedrockChunk;
import net.raphimc.viabedrock.api.chunk.BlockEntityWithBlockState;
import net.raphimc.viabedrock.api.chunk.datapalette.BedrockBiomeArray;
import net.raphimc.viabedrock.api.chunk.datapalette.BedrockDataPalette;
import net.raphimc.viabedrock.api.chunk.section.BedrockChunkSection;
import net.raphimc.viabedrock.api.chunk.section.BedrockChunkSectionImpl;
import net.raphimc.viabedrock.api.model.entity.ClientPlayerEntity;
import net.raphimc.viabedrock.api.util.PacketFactory;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ClientboundBedrockPackets;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.MovePlayerModes;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ServerMovementModes;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.SubChunkResults;
import net.raphimc.viabedrock.protocol.model.BlockChangeEntry;
import net.raphimc.viabedrock.protocol.model.Position3f;
import net.raphimc.viabedrock.protocol.rewriter.BlockEntityRewriter;
import net.raphimc.viabedrock.protocol.rewriter.DimensionIdRewriter;
import net.raphimc.viabedrock.protocol.rewriter.GameTypeRewriter;
import net.raphimc.viabedrock.protocol.storage.BlobCache;
import net.raphimc.viabedrock.protocol.storage.ChunkTracker;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;
import net.raphimc.viabedrock.protocol.storage.GameSessionStorage;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import net.raphimc.viabedrock.protocol.types.array.ByteArrayType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;

public class WorldPackets {

    private static final PacketHandler BLOCK_CHANGE_HANDLER = wrapper -> {
        final ChunkTracker chunkTracker = wrapper.user().get(ChunkTracker.class);
        final Position position = wrapper.get(Type.POSITION1_14, 0);
        final int blockState = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT); // block state
        wrapper.read(BedrockTypes.UNSIGNED_VAR_INT); // flags
        final int layer = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT); // layer
        if (layer < 0 || layer > 1) {
            wrapper.cancel();
            return;
        }

        final IntObjectPair<BlockEntity> remappedBlock = chunkTracker.handleBlockChange(position, layer, blockState);
        if (remappedBlock == null) {
            wrapper.cancel();
            return;
        }

        wrapper.write(Type.VAR_INT, remappedBlock.keyInt()); // block state

        if (remappedBlock.value() != null) {
            wrapper.send(BedrockProtocol.class);
            wrapper.cancel();
            PacketFactory.sendBlockEntityData(wrapper.user(), position, remappedBlock.value());
        }
    };

    public static void register(final BedrockProtocol protocol) {
        protocol.registerClientbound(ClientboundBedrockPackets.SET_SPAWN_POSITION, ClientboundPackets1_20_3.SPAWN_POSITION, wrapper -> {
            final ChunkTracker chunkTracker = wrapper.user().get(ChunkTracker.class);

            final int type = wrapper.read(BedrockTypes.VAR_INT); // type
            if (type != 1) { // WORLD_SPAWN
                wrapper.cancel();
                return;
            }

            final Position compassPosition = wrapper.read(BedrockTypes.BLOCK_POSITION); // compass position
            final int dimensionId = wrapper.read(BedrockTypes.VAR_INT); // dimension
            wrapper.read(BedrockTypes.BLOCK_POSITION); // spawn position

            if (chunkTracker.getDimensionId() != dimensionId) {
                wrapper.cancel();
                return;
            }

            wrapper.write(Type.POSITION1_14, compassPosition); // position
            wrapper.write(Type.FLOAT, 0F); // angle
        });
        protocol.registerClientbound(ClientboundBedrockPackets.CHANGE_DIMENSION, ClientboundPackets1_20_3.RESPAWN, wrapper -> {
            final int dimensionId = wrapper.read(BedrockTypes.VAR_INT); // dimension
            final Position3f position = wrapper.read(BedrockTypes.POSITION_3F); // position
            final boolean respawn = wrapper.read(Type.BOOLEAN); // respawn

            // TODO: Handle respawn boolean and handle keep data mask
            // TODO: Is this allowed? HiveMC does that
            if (dimensionId == wrapper.user().get(ChunkTracker.class).getDimensionId()) {
                BedrockProtocol.kickForIllegalState(wrapper.user(), "Changing dimension to the same dimension is not supported");
                return;
            }

            final GameSessionStorage gameSession = wrapper.user().get(GameSessionStorage.class);

            wrapper.user().put(new ChunkTracker(wrapper.user(), dimensionId));
            final EntityTracker oldEntityTracker = wrapper.user().get(EntityTracker.class);
            final ClientPlayerEntity clientPlayer = oldEntityTracker.getClientPlayer();
            oldEntityTracker.prepareForRespawn();
            final EntityTracker newEntityTracker = new EntityTracker(wrapper.user());
            newEntityTracker.addEntity(clientPlayer);
            wrapper.user().put(newEntityTracker);

            clientPlayer.setPosition(new Position3f(position.x(), position.y() + clientPlayer.eyeOffset(), position.z()));
            if (gameSession.getMovementMode() == ServerMovementModes.CLIENT) {
                clientPlayer.sendMovePlayerPacketToServer(MovePlayerModes.NORMAL);
            }
            clientPlayer.setChangingDimension(true);
            clientPlayer.sendPlayerPositionPacketToClient(true);

            wrapper.write(Type.STRING, DimensionIdRewriter.dimensionIdToDimensionKey(dimensionId)); // dimension type
            wrapper.write(Type.STRING, DimensionIdRewriter.dimensionIdToDimensionKey(dimensionId)); // dimension id
            wrapper.write(Type.LONG, 0L); // hashed seed
            wrapper.write(Type.BYTE, GameTypeRewriter.getEffectiveGameMode(clientPlayer.getGameType(), gameSession.getLevelGameType())); // game mode
            wrapper.write(Type.BYTE, (byte) -1); // previous game mode
            wrapper.write(Type.BOOLEAN, false); // is debug
            wrapper.write(Type.BOOLEAN, gameSession.isFlatGenerator()); // is flat
            wrapper.write(Type.OPTIONAL_GLOBAL_POSITION, null); // last death position
            wrapper.write(Type.VAR_INT, 0); // portal cooldown
            wrapper.write(Type.BYTE, (byte) 0x03); // keep data mask
        });
        protocol.registerClientbound(ClientboundBedrockPackets.LEVEL_CHUNK, null, wrapper -> {
            wrapper.cancel();
            final ChunkTracker chunkTracker = wrapper.user().get(ChunkTracker.class);
            final GameSessionStorage gameSession = wrapper.user().get(GameSessionStorage.class);

            final int chunkX = wrapper.read(BedrockTypes.VAR_INT); // chunk x
            final int chunkZ = wrapper.read(BedrockTypes.VAR_INT); // chunk z
            final int sectionCount = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT); // sub chunk count
            if (sectionCount < -2) { // Mojang client silently ignores this packet
                return;
            }

            final int startY = chunkTracker.getMinY() >> 4;
            final int endY = chunkTracker.getMaxY() >> 4;

            int requestCount = 0;
            if (sectionCount == -2) {
                requestCount = wrapper.read(BedrockTypes.UNSIGNED_SHORT_LE) + 1; // count
            } else if (sectionCount == -1) {
                requestCount = endY - startY;
            }

            final BedrockChunk previousChunk = chunkTracker.getChunk(chunkX, chunkZ);
            if (previousChunk != null) {
                chunkTracker.unloadChunk(new ChunkPosition(chunkX, chunkZ));

                if (previousChunk.isRequestSubChunks()) {
                    requestCount = endY - startY;
                }
            }

            final BedrockChunk chunk = chunkTracker.createChunk(chunkX, chunkZ, sectionCount < 0 ? requestCount : sectionCount);
            if (chunk == null) return;

            chunk.setRequestSubChunks(sectionCount < 0);

            final int fRequestCount = requestCount;
            final Consumer<byte[]> dataConsumer = combinedData -> {
                try {
                    if (fRequestCount > 0) {
                        chunkTracker.requestSubChunks(chunkX, chunkZ, startY, MathUtil.clamp(startY + fRequestCount, startY + 1, endY));
                    }

                    final ByteBuf dataBuf = Unpooled.wrappedBuffer(combinedData);

                    final BedrockChunkSection[] sections = chunk.getSections();
                    final List<BlockEntity> blockEntities = chunk.blockEntities();
                    if (dataBuf.isReadable()) {
                        try {
                            for (int i = 0; i < sectionCount; i++) {
                                sections[i].mergeWith(chunkTracker.handleBlockPalette(BedrockTypes.CHUNK_SECTION.read(dataBuf))); // chunk section
                                sections[i].applyPendingBlockUpdates(chunkTracker.airId());
                            }
                            if (gameSession.getBedrockVanillaVersion().isLowerThan("1.18.0")) {
                                final byte[] biomeData = new byte[256];
                                dataBuf.readBytes(biomeData);
                                for (ChunkSection section : sections) {
                                    section.addPalette(PaletteType.BIOMES, new BedrockBiomeArray(biomeData));
                                }
                            } else {
                                for (int i = 0; i < sections.length; i++) {
                                    BedrockDataPalette biomePalette = BedrockTypes.RUNTIME_DATA_PALETTE.read(dataBuf); // biome palette
                                    if (biomePalette == null) {
                                        if (i == 0) {
                                            throw new RuntimeException("First biome palette can not point to previous biome palette");
                                        }
                                        biomePalette = ((BedrockDataPalette) sections[i - 1].palette(PaletteType.BIOMES)).clone();
                                    }
                                    sections[i].addPalette(PaletteType.BIOMES, biomePalette);
                                }
                            }

                            dataBuf.skipBytes(1); // border blocks
                            while (dataBuf.isReadable()) {
                                final Tag tag = BedrockTypes.NETWORK_TAG.read(dataBuf); // block entity tag
                                if (tag instanceof CompoundTag) { // Ignore non-compound tags
                                    blockEntities.add(new BedrockBlockEntity((CompoundTag) tag));
                                }
                            }
                        } catch (IndexOutOfBoundsException ignored) {
                            // Mojang client stops reading at whatever point and loads whatever it has read successfully
                        } catch (Throwable e) {
                            ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Error reading chunk data", e);
                        }
                    }

                    if (!chunk.isRequestSubChunks()) {
                        chunkTracker.sendChunk(chunkX, chunkZ);
                    }
                } catch (Throwable e) {
                    throw new RuntimeException("Error handling chunk data", e);
                }
            };

            if (wrapper.read(Type.BOOLEAN)) { // caching enabled
                final Long[] blobs = wrapper.read(BedrockTypes.LONG_ARRAY); // blob ids
                final int expectedLength = sectionCount < 0 ? 1 : sectionCount + 1;
                if (blobs.length != expectedLength) { // Mojang client writes random memory contents into the request and most likely crashes
                    BedrockProtocol.kickForIllegalState(wrapper.user(), "Invalid blob count: " + blobs.length + " (expected " + expectedLength + ")");
                    return;
                }
                final byte[] data = wrapper.read(BedrockTypes.BYTE_ARRAY); // data
                wrapper.user().get(BlobCache.class).getBlob(blobs).thenAccept(blob -> {
                    final byte[] combinedData = new byte[data.length + blob.length];
                    System.arraycopy(blob, 0, combinedData, 0, blob.length);
                    System.arraycopy(data, 0, combinedData, blob.length, data.length);
                    dataConsumer.accept(combinedData);
                });
            } else {
                dataConsumer.accept(wrapper.read(BedrockTypes.BYTE_ARRAY)); // data
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.SUB_CHUNK, null, wrapper -> {
            wrapper.cancel();
            final ChunkTracker chunkTracker = wrapper.user().get(ChunkTracker.class);

            final boolean cachingEnabled = wrapper.read(Type.BOOLEAN); // caching enabled
            final int dimensionId = wrapper.read(BedrockTypes.VAR_INT); // dimension id
            if (dimensionId != chunkTracker.getDimensionId()) {
                return;
            }

            final Position center = wrapper.read(BedrockTypes.POSITION_3I); // center position
            final long count = wrapper.read(BedrockTypes.UNSIGNED_INT_LE); // count

            for (long i = 0; i < count; i++) {
                final Position offset = wrapper.read(BedrockTypes.SUB_CHUNK_OFFSET); // offset
                final Position absolute = new Position(center.x() + offset.x(), center.y() + offset.y(), center.z() + offset.z());
                final byte result = wrapper.read(Type.BYTE); // result
                final byte[] data = result != SubChunkResults.SUCCESS_ALL_AIR || !cachingEnabled ? wrapper.read(BedrockTypes.BYTE_ARRAY) : new byte[0]; // data
                final byte heightmapResult = wrapper.read(Type.BYTE); // heightmap result
                if (heightmapResult == 1) { // HAS_DATA
                    wrapper.read(new ByteArrayType(256)); // heightmap data
                }

                final Consumer<byte[]> dataConsumer = combinedData -> {
                    try {
                        if (result == SubChunkResults.SUCCESS_ALL_AIR) {
                            if (chunkTracker.mergeSubChunk(absolute.x(), absolute.y(), absolute.z(), new BedrockChunkSectionImpl(), new ArrayList<>())) {
                                chunkTracker.sendChunkInNextTick(absolute.x(), absolute.z());
                            }
                        } else if (result == SubChunkResults.SUCCESS) {
                            final ByteBuf dataBuf = Unpooled.wrappedBuffer(combinedData);

                            BedrockChunkSection section = new BedrockChunkSectionImpl();
                            final List<BedrockBlockEntity> blockEntities = new ArrayList<>();
                            try {
                                section = BedrockTypes.CHUNK_SECTION.read(dataBuf); // chunk section
                                while (dataBuf.isReadable()) {
                                    final Tag tag = BedrockTypes.NETWORK_TAG.read(dataBuf); // block entity tag
                                    if (tag instanceof CompoundTag) { // Ignore non-compound tags
                                        blockEntities.add(new BedrockBlockEntity((CompoundTag) tag));
                                    }
                                }
                            } catch (IndexOutOfBoundsException ignored) {
                                // Mojang client stops reading at whatever point and loads whatever it has read successfully
                            } catch (Throwable e) {
                                ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Error reading sub chunk data", e);
                            }
                            if (chunkTracker.mergeSubChunk(absolute.x(), absolute.y(), absolute.z(), section, blockEntities)) {
                                chunkTracker.sendChunkInNextTick(absolute.x(), absolute.z());
                            }
                        } else {
                            ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Received sub chunk with result " + result);
                            chunkTracker.requestSubChunk(absolute.x(), absolute.y(), absolute.z());
                        }
                    } catch (Throwable e) {
                        throw new RuntimeException("Error handling sub chunk data", e);
                    }
                };

                if (cachingEnabled) {
                    final long hash = wrapper.read(BedrockTypes.LONG_LE); // blob id
                    wrapper.user().get(BlobCache.class).getBlob(hash).thenAccept(blob -> {
                        if (data.length == 0) {
                            dataConsumer.accept(blob);
                        } else if (blob.length == 0) {
                            dataConsumer.accept(data);
                        } else {
                            final byte[] combinedData = new byte[data.length + blob.length];
                            System.arraycopy(blob, 0, combinedData, 0, blob.length);
                            System.arraycopy(data, 0, combinedData, blob.length, data.length);
                            dataConsumer.accept(combinedData);
                        }
                    });
                } else {
                    dataConsumer.accept(data);
                }
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.UPDATE_BLOCK, ClientboundPackets1_20_3.BLOCK_CHANGE, new PacketHandlers() {
            @Override
            protected void register() {
                map(BedrockTypes.BLOCK_POSITION, Type.POSITION1_14); // position
                handler(BLOCK_CHANGE_HANDLER);
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.UPDATE_BLOCK_SYNCED, ClientboundPackets1_20_3.BLOCK_CHANGE, new PacketHandlers() {
            @Override
            protected void register() {
                map(BedrockTypes.BLOCK_POSITION, Type.POSITION1_14); // position
                handler(BLOCK_CHANGE_HANDLER);
                read(BedrockTypes.UNSIGNED_VAR_LONG); // runtime entity id
                read(BedrockTypes.UNSIGNED_VAR_LONG); // block sync type
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.UPDATE_SUB_CHUNK_BLOCKS, null, wrapper -> {
            wrapper.cancel(); // Need multiple packets because offsets can go over chunk boundaries
            final ChunkTracker chunkTracker = wrapper.user().get(ChunkTracker.class);
            wrapper.read(BedrockTypes.BLOCK_POSITION); // position | Seems to be unused by the Mojang client
            final BlockChangeEntry[][] blockUpdatesArray = new BlockChangeEntry[2][];
            blockUpdatesArray[0] = wrapper.read(BedrockTypes.BLOCK_CHANGE_ENTRY_ARRAY); // standard blocks
            blockUpdatesArray[1] = wrapper.read(BedrockTypes.BLOCK_CHANGE_ENTRY_ARRAY); // extra blocks

            final Map<Position, List<BlockChangeRecord>> blockChanges = new HashMap<>();
            final Map<Position, BlockEntity> blockEntities = new HashMap<>();
            for (int layer = 0; layer < blockUpdatesArray.length; layer++) {
                for (BlockChangeEntry entry : blockUpdatesArray[layer]) {
                    final IntObjectPair<BlockEntity> remappedBlock = chunkTracker.handleBlockChange(entry.position(), layer, entry.blockState());
                    if (remappedBlock == null) {
                        continue;
                    }
                    if (remappedBlock.value() != null) {
                        blockEntities.put(entry.position(), remappedBlock.value());
                    }

                    final Position chunkPosition = new Position(entry.position().x() >> 4, entry.position().y() >> 4, entry.position().z() >> 4);
                    final Position relative = new Position(entry.position().x() & 0xF, entry.position().y() & 0xF, entry.position().z() & 0xF);
                    blockChanges.computeIfAbsent(chunkPosition, k -> new ArrayList<>()).add(new BlockChangeRecord1_16_2(relative.x(), relative.y(), relative.z(), remappedBlock.keyInt()));
                }
            }

            for (Map.Entry<Position, List<BlockChangeRecord>> entry : blockChanges.entrySet()) {
                final Position chunkPosition = entry.getKey();
                final List<BlockChangeRecord> changes = entry.getValue();
                long chunkKey = (chunkPosition.x() & 0x3FFFFFL) << 42;
                chunkKey |= (chunkPosition.z() & 0x3FFFFFL) << 20;
                chunkKey |= (chunkPosition.y() & 0xFFFL);

                final PacketWrapper multiBlockChange = wrapper.create(ClientboundPackets1_20_3.MULTI_BLOCK_CHANGE);
                multiBlockChange.write(Type.LONG, chunkKey); // chunk position
                multiBlockChange.write(Type.VAR_LONG_BLOCK_CHANGE_RECORD_ARRAY, changes.toArray(new BlockChangeRecord[0])); // block change records
                multiBlockChange.send(BedrockProtocol.class);
            }
            for (Map.Entry<Position, BlockEntity> entry : blockEntities.entrySet()) {
                PacketFactory.sendBlockEntityData(wrapper.user(), entry.getKey(), entry.getValue());
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.BLOCK_ENTITY_DATA, ClientboundPackets1_20_3.BLOCK_ENTITY_DATA, new PacketHandlers() {
            @Override
            protected void register() {
                map(BedrockTypes.BLOCK_POSITION, Type.POSITION1_14); // position
                handler(wrapper -> {
                    final Tag tag = wrapper.read(BedrockTypes.NETWORK_TAG); // block entity tag
                    if (!(tag instanceof CompoundTag)) {
                        wrapper.cancel();
                        return;
                    }

                    final ChunkTracker chunkTracker = wrapper.user().get(ChunkTracker.class);
                    final BedrockBlockEntity bedrockBlockEntity = new BedrockBlockEntity(wrapper.get(Type.POSITION1_14, 0), (CompoundTag) tag);
                    chunkTracker.addBlockEntity(bedrockBlockEntity);

                    final BlockEntity javaBlockEntity = BlockEntityRewriter.toJava(wrapper.user(), chunkTracker.getBlockState(bedrockBlockEntity.position()), bedrockBlockEntity);
                    if (javaBlockEntity instanceof BlockEntityWithBlockState) {
                        final BlockEntityWithBlockState blockEntityWithBlockState = (BlockEntityWithBlockState) javaBlockEntity;
                        if (blockEntityWithBlockState.hasBlockState()) {
                            final PacketWrapper blockChange = PacketWrapper.create(ClientboundPackets1_20_3.BLOCK_CHANGE, wrapper.user());
                            blockChange.write(Type.POSITION1_14, bedrockBlockEntity.position()); // position
                            blockChange.write(Type.VAR_INT, blockEntityWithBlockState.blockState()); // block state
                            blockChange.send(BedrockProtocol.class);
                        }
                    }

                    if (javaBlockEntity != null && javaBlockEntity.tag() != null) {
                        wrapper.write(Type.VAR_INT, javaBlockEntity.typeId()); // type
                        wrapper.write(Type.COMPOUND_TAG, javaBlockEntity.tag()); // block entity tag
                    } else {
                        wrapper.cancel();
                    }
                });
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.NETWORK_CHUNK_PUBLISHER_UPDATE, ClientboundPackets1_20_3.UPDATE_VIEW_DISTANCE, wrapper -> {
            final Position position = wrapper.read(BedrockTypes.POSITION_3I); // center position
            final int radius = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT) >> 4; // radius
            wrapper.write(Type.VAR_INT, radius); // radius

            final ChunkTracker chunkTracker = wrapper.user().get(ChunkTracker.class);
            chunkTracker.setRadius(radius);
            chunkTracker.setCenter(position.x() >> 4, position.z() >> 4);

            final PacketWrapper updateViewPosition = wrapper.create(ClientboundPackets1_20_3.UPDATE_VIEW_POSITION);
            updateViewPosition.write(Type.VAR_INT, position.x() >> 4); // chunk x
            updateViewPosition.write(Type.VAR_INT, position.z() >> 4); // chunk z
            updateViewPosition.send(BedrockProtocol.class);

            final int count = wrapper.read(BedrockTypes.INT_LE); // saved chunks count
            for (int i = 0; i < count; i++) {
                wrapper.read(BedrockTypes.VAR_INT); // chunk x
                wrapper.read(BedrockTypes.VAR_INT); // chunk z
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.CHUNK_RADIUS_UPDATED, ClientboundPackets1_20_3.UPDATE_VIEW_DISTANCE, new PacketHandlers() {
            @Override
            public void register() {
                map(BedrockTypes.VAR_INT, Type.VAR_INT); // radius
                handler(wrapper -> wrapper.user().get(ChunkTracker.class).setRadius(wrapper.get(Type.VAR_INT, 0)));
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.SET_TIME, ClientboundPackets1_20_3.TIME_UPDATE, new PacketHandlers() {
            @Override
            public void register() {
                map(BedrockTypes.VAR_INT, Type.LONG); // game time
                handler(wrapper -> wrapper.write(Type.LONG, wrapper.get(Type.LONG, 0) % 24000L)); // time of day
            }
        });
    }

}
