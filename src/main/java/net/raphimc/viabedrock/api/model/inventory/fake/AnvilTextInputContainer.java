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
package net.raphimc.viabedrock.api.model.inventory.fake;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.libs.mcstructs.text.ATextComponent;
import net.raphimc.viabedrock.protocol.data.enums.MenuType;

import java.util.function.Consumer;

public class AnvilTextInputContainer extends FakeContainer {

    private final Consumer<String> onRename;

    public AnvilTextInputContainer(final UserConnection user, final ATextComponent title, final Consumer<String> onRename) {
        super(user, MenuType.DO_NOT_USE_ANVIL, title);

        this.onRename = onRename;
    }

    @Override
    public void onAnvilRename(final String name) {
        this.onRename.accept(name);
    }

    @Override
    public Item[] getJavaItems(final UserConnection user) {
        throw new UnsupportedOperationException();
    }

}
