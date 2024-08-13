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
package net.raphimc.viabedrock.api.model.container.fake;

import com.viaversion.nbt.tag.Tag;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataContainer;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataKey;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.minecraft.item.StructuredItem;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.libs.mcstructs.core.TextFormatting;
import com.viaversion.viaversion.libs.mcstructs.text.ATextComponent;
import net.lenni0451.mcstructs_bedrock.forms.AForm;
import net.lenni0451.mcstructs_bedrock.forms.elements.*;
import net.lenni0451.mcstructs_bedrock.forms.types.ActionForm;
import net.lenni0451.mcstructs_bedrock.forms.types.CustomForm;
import net.lenni0451.mcstructs_bedrock.forms.types.ModalForm;
import net.lenni0451.mcstructs_bedrock.text.utils.BedrockTextUtils;
import net.raphimc.viabedrock.api.util.MathUtil;
import net.raphimc.viabedrock.api.util.StringUtil;
import net.raphimc.viabedrock.api.util.TextUtil;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ServerboundBedrockPackets;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ContainerType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ModalFormCancelReason;
import net.raphimc.viabedrock.protocol.data.enums.java.ClickType;
import net.raphimc.viabedrock.protocol.storage.InventoryTracker;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

import java.util.ArrayList;
import java.util.List;

public class NewFormContainer extends FakeContainer {

    private static final int SIZE = 54, MAX_PAGES = 256;

    private final int formId;
    private final AForm form;

    private Item[] formItems;
    private int currentPage = 0, totalPage = 0;
    private boolean sentResponse = false;

    public Item[][][] inventory = new Item[MAX_PAGES][6][9];

    private final List<Integer[]> itemMap = new ArrayList<>();

    public NewFormContainer(final UserConnection user, final int formId, final AForm form) {
        super(user, ContainerType.CONTAINER, TextUtil.stringToTextComponent("§f\uF808\uFDDC§8" + StringUtil.buildNegativePixels(169) + form.getTitle()));

        this.formId = formId;
        this.form = form;

        updateFormItems();
    }

    @Override
    public void close() {
        this.sendModalFormResponse(false);
        super.close();
    }

    @Override
    public boolean handleClick(int revision, short slot, byte button, ClickType action) {
        if (action != ClickType.PICKUP) return false;

        if (slot == 45 && currentPage > 0) {
            currentPage--;
            updatePage();
            return false;
        } else if (slot == 53 && currentPage < totalPage) {
            currentPage++;
            updatePage();
            return false;
        } else if (slot == 52 && this.form instanceof CustomForm) {
            this.close();
            return true;
        }

        int intSlot = slot;
        int index = -1;

        int pickedY = intSlot / 9;

        for (int i = 0; i < itemMap.size(); i++) {
            boolean found = false, isIndex = true;
            for (int validSlot : itemMap.get(i)) {
                if (isIndex && validSlot != currentPage) {
                    break;
                }

                if (intSlot == validSlot) {
                    found = true;
                    break;
                }

                isIndex = false;
            }

            if (found) {
                index = i;
                break;
            }
        }

        if (index == -1)
            return false;

        if (this.form instanceof ModalForm modalForm) {
            modalForm.setClickedButton(-1);
            if (pickedY == 0)
                modalForm.setClickedButton(0);
            else if (pickedY == 1)
                modalForm.setClickedButton(1);

            if (modalForm.getClickedButton() != -1) {
                this.close();
                return true;
            }
        } else if (this.form instanceof ActionForm actionForm) {
            actionForm.setClickedButton(index);
            this.close();
            return true;
        } else if (this.form instanceof CustomForm customForm) {
            if (index > customForm.getElements().length) return false;
            final AFormElement element = customForm.getElements()[index];

            if (element instanceof CheckboxFormElement checkbox) {
                if (button != 0) return false;

                checkbox.setChecked(!checkbox.isChecked());
            } else if (element instanceof DropdownFormElement dropdown) {
                if (button != 0 && button != 1) return false;

                final int selected = MathUtil.clamp(dropdown.getSelected(), -1, dropdown.getOptions().length);
                final int newSelected = selected + (button == 0 ? 1 : -1);
                if (newSelected >= dropdown.getOptions().length || selected == -1) {
                    dropdown.setSelected(0);
                } else if (newSelected < 0 || selected == dropdown.getOptions().length) {
                    dropdown.setSelected(dropdown.getOptions().length - 1);
                } else {
                    dropdown.setSelected(newSelected);
                }
            } else if (element instanceof SliderFormElement slider) {
                if (button != 0 && button != 1) return false;

                final float value = slider.getCurrent();
                final float newValue = MathUtil.clamp(value + (button == 0 ? slider.getStep() : -slider.getStep()), slider.getMin(), slider.getMax());
                slider.setCurrent(Math.round(newValue * 1000000F) / 1000000F);
            } else if (element instanceof StepSliderFormElement stepSlider) {
                if (button != 0 && button != 1) return false;

                final int selected = MathUtil.clamp(stepSlider.getSelected(), -1, stepSlider.getSteps().length);
                final int newSelected = selected + (button == 0 ? 1 : -1);
                if (newSelected >= stepSlider.getSteps().length || selected == -1) {
                    stepSlider.setSelected(0);
                } else if (newSelected < 0 || selected == stepSlider.getSteps().length) {
                    stepSlider.setSelected(stepSlider.getSteps().length - 1);
                } else {
                    stepSlider.setSelected(newSelected);
                }
            } else if (element instanceof TextFieldFormElement textField) {
                this.user.get(InventoryTracker.class).openContainer(new AnvilTextInputContainer(this.user, TextUtil.stringToTextComponent("Edit text"), textField::setValue) {
                    @Override
                    public Item[] getJavaItems() {
                        final List<Item> items = new ArrayList<>();
                        final List<String> description = new ArrayList<>();
                        description.add("§7Description: " + textField.getText());
                        description.add("§7Element: TextField");
                        description.add("§9Close GUI to save");
                        items.add(NewFormContainer.this.createItem("minecraft:paper", -1, textField.getValue(), description.toArray(new String[0])));
                        return items.toArray(new Item[0]);
                    }

                    @Override
                    public void onClosed() {
                        NewFormContainer.this.updateFormItems();
                        super.onClosed();
                    }
                });
            }

            this.updateFormItems();
        }


        return false;
    }

    private void updateFormItems() {
        this.formItems = new Item[SIZE];
        this.itemMap.clear();

        if (this.form instanceof ModalForm modalForm) {
            if (!modalForm.getText().isBlank()) {
                int size = Math.min(4, (modalForm.getText().length() / 65) + 1);
                findBestSlot("Text", 9, size, 0, modalForm.getText());
            }

            findBestSlot(modalForm.getButton1(), 9, 1, 0);
            findBestSlot(modalForm.getButton1(), 9, 1, 0);
        } else if (this.form instanceof ActionForm actionForm) {
            if (!actionForm.getText().isBlank()) {
                int size = Math.min(4, (actionForm.getText().length() / 65) + 1);
                findBestSlot("Text", 9, size, 0, actionForm.getText());
            }

            for (final ActionForm.Button button : actionForm.getButtons()) {
                String text = button.getText();
                String pathImage = button.getImage() == null ? "" :
                        button.getImage().getType() == FormImage.Type.PATH ? button.getImage().getValue() : "";
                if (text.contains("grid_tile") || text.contains("big_button")) {
                    String filteredText = text.replace("grid_tile", "").replace("big_button", "");

                    findBestSlot(filteredText, pathImage, 3, 3, 0);
                } else {
                    pathImage = "";
                    findBestSlot(button.getText(), pathImage, 9, 1, 0);
                }
            }
        } else if (this.form instanceof CustomForm customForm) {
            for (AFormElement element : customForm.getElements()) {
                if (element instanceof CheckboxFormElement checkbox) {
                    // TODO: implement better checkbox (later)
                    final List<String> description = new ArrayList<>();
                    description.add("§7Element: Checkbox");
                    description.add("§9Left click: §6Toggle");
                    if (checkbox.isChecked())
                        description.add(0, "Checked: §atrue");
                    else description.add(0, "Checked: §cfalse");
                    findBestSlot(checkbox.getText(), 3, 1, 0, description.toArray(new String[0]));
                } else if (element instanceof DropdownFormElement dropdown) {
                    final List<String> description = new ArrayList<>();
                    description.add("§7Options:");
                    for (int i = 0; i < dropdown.getOptions().length; i++) {
                        final String option = dropdown.getOptions()[i];
                        if (dropdown.getSelected() == i) {
                            description.add("§a§l" + option);
                        } else {
                            description.add("§c" + option);
                        }
                    }
                    description.add("§7Element: Dropdown");
                    description.add("§9Left click: §6Go to next option");
                    description.add("§9Right click: §6Go to previous option");

                    findBestSlot(dropdown.getText(), 9, 1, 0, description.toArray(new String[0]));
                } else if (element instanceof LabelFormElement label) {
                    findBestSlot("Text", 9, 1, 0, label.getText());
                } else if (element instanceof SliderFormElement slider) {
                    final List<String> description = new ArrayList<>();
                    description.add("§7Current value: §a" + slider.getCurrent());
                    description.add("§7Min value: §a" + slider.getMin());
                    description.add("§7Max value: §a" + slider.getMax());
                    description.add("§7Element: Slider");
                    description.add("§9Left click: §6Increase value by " + slider.getStep());
                    description.add("§9Right click: §6Decrease value by " + slider.getStep());

                    findBestSlot(slider.getText(), 9, 1, 0, description.toArray(new String[0]));
                } else if (element instanceof StepSliderFormElement stepSlider) {
                    final List<String> description = new ArrayList<>();
                    description.add("§7Options:");
                    for (int i = 0; i < stepSlider.getSteps().length; i++) {
                        final String option = stepSlider.getSteps()[i];
                        if (stepSlider.getSelected() == i) {
                            description.add("§a§l" + option);
                        } else {
                            description.add("§c" + option);
                        }
                    }
                    description.add("§7Element: StepSlider");
                    description.add("§9Left click: §6Go to next option");
                    description.add("§9Right click: §6Go to previous option");

                    findBestSlot(stepSlider.getText(), 9, 1, 0, description.toArray(new String[0]));
                } else if (element instanceof TextFieldFormElement textField) {
                    final List<String> description = new ArrayList<>();
                    description.add("§7Current value: §a" + textField.getValue());
                    description.add("§7Element: TextField");
                    description.add("§9Left click: §6Edit text");

                    findBestSlot(textField.getText(), 9, 1, 0, description.toArray(new String[0]));
                } else {
                    throw new IllegalArgumentException("Unknown form element type: " + element.getClass().getSimpleName());
                }
            }
        } else {
            throw new IllegalArgumentException("Unknown form type: " + this.form.getClass().getSimpleName());
        }

        updatePage();
    }

    private void updatePage() {
        Item[][] page = inventory[currentPage];
        for (int yl = 0; yl < page.length; yl++) {
            Item[] y = page[yl];
            for (int x = 0; x < y.length; x++) {
                Item item = y[x];
                if (item == null)
                    item = StructuredItem.empty();

                if (x == 0 && yl == 5 && currentPage > 0) {
                    item = this.createItem("minecraft:item_frame", "back_page_item".hashCode(), "Last Page");
                } else if (x == 8 && yl == 5 && currentPage < totalPage) {
                    item = this.createItem("minecraft:item_frame", "next_page_item".hashCode(), "Next Page");
                } else if (x == 7 && yl == 5 && this.form instanceof CustomForm) {
                    item = this.createItem("minecraft:item_frame", "confirm_button".hashCode(), "Sumbit");
                }

                this.formItems[yl * y.length + x] = item;
            }
        }
    }

    @Override
    public Item[] getJavaItems() {
        final Item[] items;
        items = new Item[SIZE];
        for (int i = 0; i < this.formItems.length; i++) {
            Item item = this.formItems[i];

            items[i] = item.copy();
        }
        return items;
    }

    private Item createItem(final String identifier, int modelData, final String name, final String... description) {
        final int id = BedrockProtocol.MAPPINGS.getJavaItems().getOrDefault(identifier, -1);
        if (id == -1) {
            throw new IllegalStateException("Unable to find item with identifier: " + identifier);
        }

        final StructuredDataContainer data = ProtocolConstants.createStructuredDataContainer();
        data.set(StructuredDataKey.ITEM_NAME, this.stringToNbt(name.replace("\n", " | ")));

        if (modelData != -1) {
            data.set(StructuredDataKey.CUSTOM_MODEL_DATA, Math.abs(modelData) + 1);
        }

        if (description.length > 0) {
            final List<Tag> loreTags = new ArrayList<>();
            for (String desc : description) {
                for (final String line : BedrockTextUtils.split(desc, "\n")) {
                    loreTags.add(this.stringToNbt(line));
                }
            }
            data.set(StructuredDataKey.LORE, loreTags.toArray(new Tag[0]));
        }

        return new StructuredItem(id, 1, data);
    }

    private Tag stringToNbt(final String text) {
        final ATextComponent component = TextUtil.stringToTextComponent(text);
        if (component.getStyle().getColor() == null) {
            component.getStyle().setFormatting(TextFormatting.WHITE);
        }
        component.getStyle().setItalic(false);
        return TextUtil.textComponentToNbt(component);
    }

    private int[] findBestSlot(String name, int requiredX, int requiredY, int page, String... description) {
        return findBestSlot(name, "", requiredX, requiredY, page, description);
    }

    // TODO: this doesn't seems optimized, fix that?
    private int[] findBestSlot(String name, String path, int requiredX, int requiredY, int page, String... description) {
        int slotX = -1, slotY = -1;
        boolean found = false;

        Item[][] inventory = this.inventory[page];
        for (int y = 0; y <= inventory.length - requiredY && !found; y++) {
            for (int x = 0; x <= inventory[y].length - requiredX; x++) {
                boolean isSpaceAvailable = true;

                for (int yy = 0; yy < requiredY; yy++) {
                    for (int xx = 0; xx < requiredX; xx++) {
                        if (inventory[y + yy][x + xx] != null) {
                            isSpaceAvailable = false;
                            break;
                        }
                    }
                }

                if (isSpaceAvailable) {
                    slotX = x;
                    slotY = y;

                    found = true;
                    break;
                }
            }
        }

        if (slotX != -1 && slotY != -1) {
            Integer[] integers = new Integer[requiredX * requiredY + 1];
            integers[0] = totalPage;
            int count = 1;
            for (int y = slotY; y < slotY + requiredY; y++) {
                for (int x = slotX; x < slotX + requiredX; x++) {
                    int modelData = getIdentifierBasedSlot(requiredY == 1, x, y, slotX + requiredX - 1, slotY + requiredY - 1).hashCode();
                    Item item = createItem("minecraft:item_frame", modelData, name, description);

                    inventory[y][x] = item;
                    integers[count] = y * 9 + x;

                    count++;
                }
            }

            this.itemMap.add(integers);

            if (!path.isEmpty()) {
                // TODO: some servers like lifeboat use minecraft already existing texture, implement that!
                final String[] splitName = path.split("/");
                String simpleName = splitName[splitName.length - 1];
                int modelData = Math.abs(("ui/" + simpleName).hashCode()) + 1;
                Item item = createItem("minecraft:map", modelData, name, description);
                int centerX = (int) Math.floor(slotX + requiredX / 2);
                int centerY = (int) Math.floor(slotY + requiredY / 2);

                inventory[centerY][centerX] = item;
            }
        } else {
            totalPage++;
            return findBestSlot(name, requiredX, requiredY, totalPage);
        }

        return new int[] { slotX, slotY };
    }

    // TODO: please clean this up
    private String getIdentifierBasedSlot(boolean isOneColumn, int minX, int minY, int maxX, int maxY) {
        int minSlotX = maxX - minX, minSlotY = maxY - minY;

        if (minSlotY == maxY) {
            if (minSlotX == 0) {
                return isOneColumn ? "end_button" : "upper_right_slot";
            } else if (minSlotX == maxX) {
                return isOneColumn ? "start_button" : "upper_left_slot";
            } else {
                return isOneColumn ? "middle_button" : "upper_slot";
            }
        } else if (minSlotY == 0 && minSlotY != maxY) {
            if (minSlotX == 0) {
                return isOneColumn ? "end_button" : "down_right_slot";
            } else if (minSlotX == maxX) {
                return isOneColumn ? "start_button" : "down_left_slot";
            }
        } else {
            if (minSlotX == maxX) {
                return "left_slot";
            } else if (minSlotX == 0) {
                return "right_slot";
            }
        }

        if (minSlotY == 0) {
            return isOneColumn ? "middle_button" : "bottom_slot";
        } else if (minSlotY == maxY) {
            return "upper_slot";
        }

        return "empty_slot";
    }

    @Override
    public void onClosed() {
        this.sendModalFormResponse(true);
    }

    private void sendModalFormResponse(final boolean userClosed) {
        if (this.sentResponse) return;
        this.sentResponse = true;

        final PacketWrapper modalFormResponse = PacketWrapper.create(ServerboundBedrockPackets.MODAL_FORM_RESPONSE, this.user);
        modalFormResponse.write(BedrockTypes.UNSIGNED_VAR_INT, this.formId); // id
        modalFormResponse.write(Types.BOOLEAN, !userClosed); // has response
        if (!userClosed) {
            modalFormResponse.write(BedrockTypes.STRING, this.form.serializeResponse() + "\n"); // response
            modalFormResponse.write(Types.BOOLEAN, false); // has cancel reason
        } else {
            modalFormResponse.write(Types.BOOLEAN, true); // has cancel reason
            modalFormResponse.write(Types.BYTE, (byte) ModalFormCancelReason.UserClosed.getValue()); // cancel reason
        }
        modalFormResponse.sendToServer(BedrockProtocol.class);
    }

}
