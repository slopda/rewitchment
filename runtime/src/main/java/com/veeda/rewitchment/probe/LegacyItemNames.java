package com.veeda.rewitchment.probe;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class LegacyItemNames {
    private LegacyItemNames() {}

    public static ItemStack setCustomName(ItemStack stack, Text name) {
        if (name == null) {
            stack.remove(DataComponentTypes.CUSTOM_NAME);
        } else {
            stack.set(DataComponentTypes.CUSTOM_NAME, name);
        }
        return stack;
    }

    public static boolean hasCustomName(ItemStack stack) {
        return stack.contains(DataComponentTypes.CUSTOM_NAME);
    }
}
