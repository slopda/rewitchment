package com.veeda.rewitchment.probe;

import java.util.UUID;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

public final class LegacyItemNbt {
    private LegacyItemNbt() {}

    public static boolean hasLegacyNbt(ItemStack stack) {
        return stack.contains(DataComponentTypes.CUSTOM_DATA);
    }

    public static void putBoolean(ItemStack stack, String key, boolean value) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putBoolean(key, value));
    }

    public static void putInt(ItemStack stack, String key, int value) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putInt(key, value));
    }

    public static void putString(ItemStack stack, String key, String value) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putString(key, value));
    }

    public static void putUuid(ItemStack stack, String key, UUID value) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putUuid(key, value));
    }

    public static void remove(ItemStack stack, String key) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.remove(key));
    }

    private static NbtCompound present(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) {
            throw new NullPointerException("Legacy ItemStack.getNbt() returned null");
        }
        return component.copyNbt();
    }

    private static NbtCompound orCreate(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
    }

    public static boolean contains(ItemStack stack, String key) {
        return present(stack).contains(key);
    }

    public static boolean getOrCreateContains(ItemStack stack, String key) {
        return orCreate(stack).contains(key);
    }

    public static boolean getBoolean(ItemStack stack, String key) {
        return present(stack).getBoolean(key);
    }

    public static boolean getOrCreateBoolean(ItemStack stack, String key) {
        return orCreate(stack).getBoolean(key);
    }

    public static int getInt(ItemStack stack, String key) {
        return present(stack).getInt(key);
    }

    public static int getOrCreateInt(ItemStack stack, String key) {
        return orCreate(stack).getInt(key);
    }

    public static long getLong(ItemStack stack, String key) {
        return present(stack).getLong(key);
    }

    public static long getOrCreateLong(ItemStack stack, String key) {
        return orCreate(stack).getLong(key);
    }

    public static String getString(ItemStack stack, String key) {
        return present(stack).getString(key);
    }

    public static String getOrCreateString(ItemStack stack, String key) {
        return orCreate(stack).getString(key);
    }

    public static UUID getUuid(ItemStack stack, String key) {
        return present(stack).getUuid(key);
    }

    public static UUID getOrCreateUuid(ItemStack stack, String key) {
        return orCreate(stack).getUuid(key);
    }

    public static void setUnbreakable(ItemStack stack, String key, boolean enabled) {
        if (!"Unbreakable".equals(key)) {
            throw new IllegalArgumentException("Expected the legacy Unbreakable key");
        }
        if (enabled) {
            stack.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(true));
        } else {
            stack.remove(DataComponentTypes.UNBREAKABLE);
        }
    }
}
