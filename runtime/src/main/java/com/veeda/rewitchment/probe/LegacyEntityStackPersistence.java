package com.veeda.rewitchment.probe;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

public final class LegacyEntityStackPersistence {
    private LegacyEntityStackPersistence() {}

    public static ItemStack readEntityStack(NbtCompound data, Entity carrier) {
        return ItemStack.fromNbtOrEmpty(carrier.getWorld().getRegistryManager(), data);
    }

    public static NbtCompound writeEntityStack(
            ItemStack stack, NbtCompound into, Entity carrier) {
        NbtElement encoded = stack.encodeAllowEmpty(carrier.getWorld().getRegistryManager());
        if (!(encoded instanceof NbtCompound compound)) {
            throw new IllegalStateException("Expected ItemStack NBT compound");
        }
        return into.copyFrom(compound);
    }
}
