package com.veeda.rewitchment.probe;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;

public final class LegacyEnchantmentQueries {
    private LegacyEnchantmentQueries() {}

    public static boolean hasSilkTouch(ItemStack stack) {
        return EnchantmentHelper.getEnchantments(stack).getEnchantments()
                .stream().anyMatch(entry -> entry.matchesKey(Enchantments.SILK_TOUCH));
    }

    public static Map<Enchantment, Integer> legacyLevels(ItemStack stack) {
        ItemEnchantmentsComponent components = EnchantmentHelper.getEnchantments(stack);
        Map<Enchantment, Integer> result = new LinkedHashMap<>();
        for (RegistryEntry<Enchantment> entry : components.getEnchantments()) {
            result.put(entry.value(), components.getLevel(entry));
        }
        return result;
    }

    public static int getLegacyLevel(Enchantment enchantment, ItemStack stack) {
        ItemEnchantmentsComponent components = EnchantmentHelper.getEnchantments(stack);
        for (RegistryEntry<Enchantment> entry : components.getEnchantments()) {
            if (entry.value() == enchantment) {
                return components.getLevel(entry);
            }
        }
        return 0;
    }

    public static void clearLegacyEnchantments(
            Map<Enchantment, Integer> newLevels, ItemStack stack) {
        if (!newLevels.isEmpty()) {
            throw new IllegalArgumentException(
                    "Legacy enchantment-object map writes are not supported");
        }
        EnchantmentHelper.set(stack, ItemEnchantmentsComponent.DEFAULT);
    }

    public static void setLegacyRepairCost(ItemStack stack, int cost) {
        stack.set(DataComponentTypes.REPAIR_COST, cost);
    }

    public static int equipmentLevel(
            RegistryKey<Enchantment> key, LivingEntity wearer) {
        var enchantments = wearer.getWorld().getRegistryManager()
                .get(RegistryKeys.ENCHANTMENT);
        return EnchantmentHelper.getEquipmentLevel(
                enchantments.entryOf(key), wearer);
    }
}
