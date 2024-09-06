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
import com.viaversion.viaversion.api.minecraft.Vector3f;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataContainer;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataKey;
import com.viaversion.viaversion.api.minecraft.entities.EntityTypes1_20_5;
import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import com.viaversion.viaversion.api.minecraft.item.StructuredItem;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.version.Types1_21;
import com.viaversion.viaversion.protocols.v1_20_5to1_21.packet.ClientboundPackets1_21;
import com.viaversion.viaversion.util.Key;
import net.raphimc.viabedrock.api.model.resourcepack.EntityDefinitions;
import net.raphimc.viabedrock.api.util.*;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ActorDataIDs;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ActorFlags;
import net.raphimc.viabedrock.protocol.model.Position3f;
import net.raphimc.viabedrock.protocol.rewriter.resourcepack.CustomEntityResourceRewriter;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;
import net.raphimc.viabedrock.protocol.storage.ResourcePacksStorage;
import org.oryxel.cube.model.bedrock.BedrockRenderController;

import java.util.*;

public class CustomEntity extends Entity {

    private final EntityDefinitions.EntityDefinition entityDefinition;
    private final List<ItemDisplayEntity> partEntities = new ArrayList<>();
    private boolean spawned;

    public CustomEntity(final UserConnection user, final long uniqueId, final long runtimeId, final int javaId, final EntityDefinitions.EntityDefinition entityDefinition) {
        super(user, uniqueId, runtimeId, javaId, UUID.randomUUID(), EntityTypes1_20_5.INTERACTION);
        this.entityDefinition = entityDefinition;
    }

    @Override
    public void updateEntityData(EntityData[] entityData) {
        final ResourcePacksStorage storage = user.get(ResourcePacksStorage.class);

        final EntityTracker entityTracker = user.get(EntityTracker.class);

        StringBuilder builder = new StringBuilder();
        EnumSet<ActorFlags> flags = EnumSet.noneOf(ActorFlags.class);
        for (EntityData data : entityData) {
            ActorDataIDs dataID = ActorDataIDs.getByValue(data.id());
            if (dataID == null) continue;

            if (dataID == ActorDataIDs.RESERVED_0) {
                flags.addAll(EntityUtil.getActorFlags(data.value()));
                continue;
            }

            // We can just handle these.
            if (!(data.value() instanceof Number) && !(data.value() instanceof Boolean))
                continue;

            Object value = data.value();
            if (data.value() instanceof Double)
                value = StringUtil.toDecimal(data.value()); // mocha can't handle scientific notation

            builder.append("query." + dataID.name().toLowerCase() + "=" + value + ";");
        }

        for (ActorFlags flag : ActorFlags.values()) {
            String value = flags.contains(flag) ? "true" : "false";
            String name = flag.name().replace("USING", "USING_")
                    .replace("NO", "NO_").replace("WALL", "WALL_").
                    replace("RETURN", "RETURN_").replace("DAMAGENEARBYMOBS",
                            "can_damage_nearby_mobs").toLowerCase();

            if (name.startsWith("IN"))
                name = name.replace("IN", "IN_");
            else if (name.startsWith("ON"))
                name = name.replace("ON", "ON_");
            else if (name.startsWith("CAN"))
                name = name.replace("ON", "ON_");

            if (name.startsWith("is_"))
                builder.append("q." + name + "=" + value + ";");
            else {
                builder.append("q." + name + "=" + value + ";");
                builder.append("q.is_" + name + "=" + value + ";");
            }
        }

        this.entityDefinition.entityData().variables().forEach(var -> builder.append(var + ";"));

        final Map<String, String> values = new HashMap<>();
        for (String controllerIdentifier : entityDefinition.entityData().controllers()) {
            BedrockRenderController controller = storage.getBedrockControllers().get(controllerIdentifier);
            if (controller == null) continue;

            String geometryName = "", textureName = "";
            for (String geo : controller.geometryIndex()) {
                String temp = getGeometryOrTexture(geo, builder, controller.geometries());
                if (entityDefinition.entityData().geometries().containsKey(temp)) geometryName = temp;
            }
            for (String texture : controller.textureIndex()) {
                String temp = getGeometryOrTexture(texture, builder, controller.textures());
                if (entityDefinition.entityData().textures().containsKey(temp)) textureName = temp;
            }

            Object o = storage.getConverterData().get("ce_" + this.entityDefinition.identifier() + "_" + textureName + "_" + geometryName);
            if (o == null) continue;

            values.put(textureName, geometryName);
        }

        updateEntityModel(values);
        super.updateEntityData(entityData);
    }

    private void updateEntityModel(Map<String, String> map) {
        this.despawn();
        this.partEntities.clear();
        spawn(map);
    }

    private String getGeometryOrTexture(String index, StringBuilder builder, Map<String, List<String>> map) {
        if (index.toLowerCase().startsWith("geometry.") || index.toLowerCase().startsWith("texture."))
            return index.split("\\.")[1];

        // This is due to mocha parsing.
        // you have to do (value > 3) ? 'value1' : 'value2' instead of value > 3 ? 'value1' : 'value2'.
        if (!index.toLowerCase().startsWith("array.")) {
            String[] split = index.split("\\?");
            if (split.length == 2) {
                index = "(" + split[0].replace(" ", "") + ") ? " + split[1];
            }
        }

        String arrayName = index.toLowerCase().startsWith("array.") ? getArrayName(index) : "";
        String fixed = builder.toString().replace(";;", ";") + index.replace(arrayName, "").replace("[", "")
                .replace("]", "").replace("q.", "query.");

        fixed = StringUtil.addQuote(fixed); // have to wrap it around a quote or else it's going to return 0.

        try {
            String eval = index.toLowerCase().startsWith("array.") ?
                    getGeometryOrTexture(fixed, arrayName, map) : getGeometryOrTexture(fixed);
            return eval;
        } catch (Exception e) {
            // e.printStackTrace();
            return "";
        }
    }

    private String getGeometryOrTexture(String parse) {
        MochaEngineUtil engine = MochaEngineUtil.build();
        String eval = engine.eval(parse).replace(".0", "");
        if (!NumberUtil.isNumber(eval)) {
            return eval.toLowerCase().replace("geometry.", "").replace("texture.", "");
        } else return eval;
    }

    private String getGeometryOrTexture(String parse, String arrayName, Map<String, List<String>> map) {
        List<String> values = map.get(arrayName);
        MochaEngineUtil engine = MochaEngineUtil.build();
        String eval = engine.eval(parse).replace(".0", "");
        if (NumberUtil.isNumber(eval)) {
            int index = Integer.parseInt(eval);
            if (index > values.size() - 1)
                index = 0;

            return values.get(index);
        } else {
            return eval.toLowerCase().replace("geometry.", "").replace("texture.", "");
        }
    }

    private String getArrayName(String parse) {
        String[] array = parse.split("\\[");
        return array[0];
    }

    @Override
    public void setPosition(final Position3f position) {
        super.setPosition(position);

        if (!this.spawned) {
            this.spawn(Map.of("default", "default"));
        } else {
            this.partEntities.forEach(ItemDisplayEntity::updatePositionAndRotation);
        }
    }

    @Override
    public void setRotation(final Position3f rotation) {
        super.setRotation(rotation);

        if (this.spawned) {
            this.partEntities.forEach(ItemDisplayEntity::updatePositionAndRotation);
        }
    }

    @Override
    public void remove() {
        super.remove();
        this.despawn();
    }

    private void spawn(Map<String, String> map) {
        this.spawned = true;
        final EntityTracker entityTracker = user.get(EntityTracker.class);
        final ResourcePacksStorage resourcePacksStorage = user.get(ResourcePacksStorage.class);

        for (Map.Entry<String, String> entry : map.entrySet()) {
            System.out.println(entry.getKey() + "," + entry.getValue());
            final Object parts = resourcePacksStorage.getConverterData().get("ce_" + this.entityDefinition.identifier() + "_" +
                    entry.getKey() + "_" + entry.getValue());
            if (parts == null)
                return;

            for (int i = 0; i < (int) parts; i++) {
                final ItemDisplayEntity partEntity = new ItemDisplayEntity(entityTracker.getNextJavaEntityId());
                this.partEntities.add(partEntity);

                final PacketWrapper addEntity = PacketWrapper.create(ClientboundPackets1_21.ADD_ENTITY, user);
                addEntity.write(Types.VAR_INT, partEntity.javaId()); // entity id
                addEntity.write(Types.UUID, partEntity.javaUuid()); // uuid
                addEntity.write(Types.VAR_INT, partEntity.type().getId()); // type id
                addEntity.write(Types.DOUBLE, (double) this.position.x()); // x
                addEntity.write(Types.DOUBLE, (double) this.position.y()); // y
                addEntity.write(Types.DOUBLE, (double) this.position.z()); // z
                addEntity.write(Types.BYTE, MathUtil.float2Byte(this.rotation.x())); // pitch
                addEntity.write(Types.BYTE, MathUtil.float2Byte(this.rotation.y())); // yaw
                addEntity.write(Types.BYTE, MathUtil.float2Byte(this.rotation.z())); // head yaw
                addEntity.write(Types.VAR_INT, 0); // data
                addEntity.write(Types.SHORT, (short) 0); // velocity x
                addEntity.write(Types.SHORT, (short) 0); // velocity y
                addEntity.write(Types.SHORT, (short) 0); // velocity z
                addEntity.send(BedrockProtocol.class);

                updateEntityModel(resourcePacksStorage, partEntity, entry.getKey(), entry.getValue(), i);
            }
        }
    }

    private void despawn() {
        this.spawned = false;
        final int[] entityIds = new int[partEntities.size()];
        for (int i = 0; i < partEntities.size(); i++) {
            entityIds[i] = partEntities.get(i).javaId();
        }
        final PacketWrapper removeEntities = PacketWrapper.create(ClientboundPackets1_21.REMOVE_ENTITIES, this.user);
        removeEntities.write(Types.VAR_INT_ARRAY_PRIMITIVE, entityIds); // entity ids
        removeEntities.send(BedrockProtocol.class);
    }

    private void updateEntityModel(ResourcePacksStorage storage, Entity entity, String texture, String geometry, int index) {
        final List<EntityData> javaEntityData = new ArrayList<>();
        final StructuredDataContainer data = ProtocolConstants.createStructuredDataContainer();
        data.set(StructuredDataKey.CUSTOM_MODEL_DATA, CustomEntityResourceRewriter.getCustomModelData(this.entityDefinition.identifier() + "_"
                + texture + "_" + geometry + "_" + index));
        final StructuredItem item = new StructuredItem(BedrockProtocol.MAPPINGS.getJavaItems().get(Key.namespaced(CustomEntityResourceRewriter.ITEM)), 1, data);
        javaEntityData.add(new EntityData(entity.getJavaEntityDataIndex("ITEM_STACK"), Types1_21.ENTITY_DATA_TYPES.itemType, item));

        final float scale = (float) storage.getConverterData().get("ce_" + this.entityDefinition.identifier() + "_" + texture + "_" + geometry + "_" + index + "_scale");
        javaEntityData.add(new EntityData(entity.getJavaEntityDataIndex("SCALE"), Types1_21.ENTITY_DATA_TYPES.vector3FType,
                new Vector3f(scale, scale, scale)));
        javaEntityData.add(new EntityData(entity.getJavaEntityDataIndex("TRANSLATION"), Types1_21.ENTITY_DATA_TYPES.vector3FType,
                new Vector3f(0F, scale * 0.5F, 0F)));

        final PacketWrapper setEntityData = PacketWrapper.create(ClientboundPackets1_21.SET_ENTITY_DATA, user);
        setEntityData.write(Types.VAR_INT, entity.javaId()); // entity id
        setEntityData.write(Types1_21.ENTITY_DATA_LIST, javaEntityData); // entity data
        setEntityData.send(BedrockProtocol.class);
    }

    private class ItemDisplayEntity extends Entity {

        public ItemDisplayEntity(final int javaId) {
            super(CustomEntity.this.user, 0L, 0L, javaId, UUID.randomUUID(), EntityTypes1_20_5.ITEM_DISPLAY);
        }

        public void updatePositionAndRotation() {
            final PacketWrapper teleportEntity = PacketWrapper.create(ClientboundPackets1_21.TELEPORT_ENTITY, this.user);
            teleportEntity.write(Types.VAR_INT, this.javaId()); // entity id
            teleportEntity.write(Types.DOUBLE, (double) CustomEntity.this.position.x()); // x
            teleportEntity.write(Types.DOUBLE, (double) CustomEntity.this.position.y()); // y
            teleportEntity.write(Types.DOUBLE, (double) CustomEntity.this.position.z()); // z
            teleportEntity.write(Types.BYTE, MathUtil.float2Byte(CustomEntity.this.rotation.y())); // yaw
            teleportEntity.write(Types.BYTE, MathUtil.float2Byte(CustomEntity.this.rotation.x())); // pitch
            teleportEntity.write(Types.BOOLEAN, CustomEntity.this.onGround); // on ground
            teleportEntity.send(BedrockProtocol.class);
        }

    }

}
