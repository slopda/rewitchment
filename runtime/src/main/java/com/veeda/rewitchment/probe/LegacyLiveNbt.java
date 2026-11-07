package com.veeda.rewitchment.probe;

import java.util.UUID;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

public final class LegacyLiveNbt extends NbtCompound {
    private final ItemStack stack;

    private LegacyLiveNbt(ItemStack stack) {
        this.stack = stack;
        super.copyFrom(stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt());
    }

    public static NbtCompound getOrCreate(ItemStack stack) {
        return new LegacyLiveNbt(stack);
    }

    public static NbtCompound get(ItemStack stack) {
        return stack.contains(DataComponentTypes.CUSTOM_DATA) ? new LegacyLiveNbt(stack) : null;
    }

    private void store() {
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(this));
    }

    @Override public NbtElement put(String key, NbtElement element) { var old = super.put(key, element); store(); return old; }
    @Override public void putByte(String key, byte value) { super.putByte(key, value); store(); }
    @Override public void putShort(String key, short value) { super.putShort(key, value); store(); }
    @Override public void putInt(String key, int value) { super.putInt(key, value); store(); }
    @Override public void putLong(String key, long value) { super.putLong(key, value); store(); }
    @Override public void putUuid(String key, UUID value) { super.putUuid(key, value); store(); }
    @Override public void putFloat(String key, float value) { super.putFloat(key, value); store(); }
    @Override public void putDouble(String key, double value) { super.putDouble(key, value); store(); }
    @Override public void putString(String key, String value) { super.putString(key, value); store(); }
    @Override public void putBoolean(String key, boolean value) { super.putBoolean(key, value); store(); }
    @Override public void putIntArray(String key, int[] value) { super.putIntArray(key, value); store(); }
    @Override public void remove(String key) { super.remove(key); store(); }

    @Override public NbtCompound copyFrom(NbtCompound source) {
        super.copyFrom(source);
        store();
        if (source instanceof LegacyLiveNbt other && other.stack.contains(DataComponentTypes.POTION_CONTENTS))
            stack.set(DataComponentTypes.POTION_CONTENTS, other.stack.get(DataComponentTypes.POTION_CONTENTS));
        return this;
    }
}
