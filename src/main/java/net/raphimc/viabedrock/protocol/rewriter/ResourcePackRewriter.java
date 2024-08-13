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
package net.raphimc.viabedrock.protocol.rewriter;

import com.viaversion.viaversion.libs.gson.JsonArray;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import net.raphimc.viabedrock.api.model.ResourcePack;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import net.raphimc.viabedrock.protocol.storage.ResourcePacksStorage;

import java.awt.image.BufferedImage;
import java.util.*;

public class ResourcePackRewriter {

    // TODO: 1.20.2 added overlay packs. Maybe that's useful?
    // TODO: 1.20.3 added the ability to load multiple separate packs
    public static ResourcePack.Content bedrockToJava(final ResourcePacksStorage resourcePacksStorage) {
        final ResourcePack.Content javaContent = new ResourcePack.Content();

        resourcePacksStorage.iterateResourcePacksBottomToTop(pack -> {
            convertGlyphSheets(pack.content(), javaContent);
            convertUITextures(pack.content(), javaContent);
            return true;
        });

        javaContent.putJson("pack.mcmeta", createPackManifest());

        return javaContent;
    }

    private static JsonObject createPackManifest() {
        final JsonObject root = new JsonObject();
        final JsonObject pack = new JsonObject();
        root.add("pack", pack);
        pack.addProperty("pack_format", ProtocolConstants.JAVA_PACK_VERSION);
        pack.addProperty("description", "ViaBedrock Resource Pack");
        return root;
    }

    // semi support for button icons...
    private static void convertUITextures(final ResourcePack.Content bedrockContent, final ResourcePack.Content javaContent) {
        Map<Integer, String> map = new HashMap<>();
        List<Integer> keys = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : bedrockContent.entrySet()) {
            String path = entry.getKey();
            if (!path.startsWith("textures/ui") || !path.endsWith(".png") && !path.endsWith(".jpg"))
                continue;

            BufferedImage image = bedrockContent.getImage(path);
            path = path.replace(".png", "").replace(".jpg", "");
            // image smaller than 16x16 might cause issues!
            if (image == null || image.getHeight() * image.getWidth() < 256)
                continue;

            final String[] splitName = path.split("/");
            String simpleName = splitName[splitName.length - 1];

            String jsonFile = entry.getKey().replace(".png", ".json").replace(".jpg", ".json");
            if (bedrockContent.containsKey(jsonFile)) // this is already used for customizing hud...
                continue;

            int modelData = Math.abs(("ui/" + simpleName).hashCode()) + 1;
            String nameWithModelData = simpleName + Math.abs(modelData);

            putImageToPath(javaContent, image, nameWithModelData);
            putModelItemToPath(javaContent, nameWithModelData);

            keys.add(modelData);
            map.put(modelData, nameWithModelData);

            System.out.println(simpleName + "," + modelData);
        }

        Collections.sort(keys);
        if (!map.isEmpty()) {
            putItemToPath(javaContent, keys, map);
        }
    }

    private static void putItemToPath(ResourcePack.Content javaContent, List<Integer> sortedKeys, Map<Integer, String> map) {
        JsonObject itemJson = new JsonObject();
        itemJson.addProperty("parent", "minecraft:item/generated");
        JsonObject o1 = new JsonObject();
        o1.addProperty("layer0", "minecraft:item/map");
        itemJson.add("textures", o1);

        JsonElement overridesElement = itemJson.get("overrides");
        JsonArray overrides;
        boolean notInit = false;
        if (overridesElement == null || !overridesElement.isJsonArray()) {
            overrides = new JsonArray();
            notInit = true;
        } else {
            overrides = overridesElement.getAsJsonArray();
        }

        for (Integer i : sortedKeys) {
            JsonObject overrideObject = new JsonObject();
            overrideObject.addProperty("model", "viabedrock/ui/" + map.get(i));
            JsonObject predicate = new JsonObject();
            predicate.addProperty("custom_model_data",i);
            overrideObject.add("predicate", predicate);
            overrides.add(overrideObject);
        }

        if (notInit) {
            itemJson.add("overrides", overrides);
        }

        javaContent.putJson("assets/minecraft/models/item/map.json", itemJson);
    }

    private static void putModelItemToPath(ResourcePack.Content javaContent, String name) {
        JsonObject itemModelJson = new JsonObject();
        itemModelJson.addProperty("parent", "minecraft:item/generated");
        JsonObject o = new JsonObject();
        o.addProperty("layer0", "minecraft:item/" + name);

        JsonObject display = new JsonObject();
        JsonObject gui = new JsonObject();
        JsonArray scale = new JsonArray();
        scale.add(3); scale.add(3); scale.add(3);
        gui.add("scale", scale);
        display.add("gui", gui);
        itemModelJson.add("display", display);

        itemModelJson.add("textures", o);
        String jsonJavaPath = "assets/minecraft/models/viabedrock/ui/" + name + ".json";
        javaContent.putJson(jsonJavaPath, itemModelJson);
    }

    private static void putImageToPath(ResourcePack.Content javaContent, BufferedImage image, String simpleName) {
        final String javaPath = "assets/minecraft/textures/item/" + simpleName + ".png";
        javaContent.putImage(javaPath, image);
    }

    // TODO: Maybe the new Unihex provider is better
    private static void convertGlyphSheets(final ResourcePack.Content bedrockContent, final ResourcePack.Content javaContent) {
        final int glyphsPerRow = 16;
        final int glyphsPerColumn = 16;

        final String javaDefaultsPath = "assets/minecraft/font/default.json";
        final JsonObject root;
        JsonArray providers;
        if (javaContent.containsKey(javaDefaultsPath)) {
            root = javaContent.getJson(javaDefaultsPath);
            providers = root.getAsJsonArray("providers");
        } else {
            root = new JsonObject();
            providers = new JsonArray();
            root.add("providers", providers);
        }

        for (int i = 0; i < 0xFF; i++) {
            final String pageName = "glyph_" + String.format("%1$02X", i) + ".png";
            final String bedrockPath = "font/" + pageName;
            if (!bedrockContent.containsKey(bedrockPath)) {
                continue;
            }

            final String javaPath = "assets/viabedrock/textures/font/" + pageName.toLowerCase(Locale.ROOT);
            final BufferedImage image = bedrockContent.getImage(bedrockPath);
            javaContent.putImage(javaPath, image);

            final int glyphHeight = image.getHeight() / glyphsPerColumn;

            for (JsonElement provider : providers) {
                if (provider.getAsJsonObject().get("file").getAsString().equals("viabedrock:font/" + pageName.toLowerCase(Locale.ROOT))) {
                    providers.remove(provider);
                    break;
                }
            }

            final JsonObject glyphPage = new JsonObject();
            providers.add(glyphPage);
            glyphPage.addProperty("type", "bitmap");
            glyphPage.addProperty("file", "viabedrock:font/" + pageName.toLowerCase(Locale.ROOT));
            glyphPage.addProperty("ascent", glyphHeight / 2 + 5);
            glyphPage.addProperty("height", glyphHeight);
            final JsonArray chars = new JsonArray();
            glyphPage.add("chars", chars);
            for (int c = 0; c < glyphsPerColumn; c++) {
                final StringBuilder row = new StringBuilder();
                for (int r = 0; r < glyphsPerRow; r++) {
                    final int idx = c * glyphsPerColumn + r;
                    row.append((char) (i << 8 | idx));
                }
                chars.add(row.toString());
            }
        }
        if (providers.isEmpty()) {
            return;
        }

        javaContent.putJson(javaDefaultsPath, root);
    }

}
