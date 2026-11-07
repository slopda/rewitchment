package com.veeda.rewitchment.probe;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.Item;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

public final class LegacyArmorRegistration {
    private record Registered(RegistryEntry<ArmorMaterial> entry, boolean explicitDurability) {}
    private static final Map<String, ArmorMaterial> MATERIALS = new HashMap<>();
    private static final Map<ArmorMaterial, Registered> ENTRIES = new IdentityHashMap<>();

    private LegacyArmorRegistration() {}

    public static ArmorMaterial hedgewitch() { return material("hedgewitch", false); }
    public static ArmorMaterial alchemist() { return material("alchemist", false); }
    public static ArmorMaterial besmirched() { return material("besmirched", false); }
    public static ArmorMaterial harbinger() { return material("harbinger", true); }

    private static synchronized ArmorMaterial material(String name, boolean harbinger) {
        ArmorMaterial existing = MATERIALS.get(name);
        if (existing != null) return existing;
        ArmorMaterial chain = ArmorMaterials.CHAIN.value();
        ArmorMaterial resilience = harbinger ? ArmorMaterials.NETHERITE.value() : chain;

        Supplier<Ingredient> repair = harbinger
                ? ArmorMaterials.NETHERITE.value().repairIngredient()
                : () -> woolRepairIngredient(name);
        ArmorMaterial result = new ArmorMaterial(Map.copyOf(chain.defense()),
                ArmorMaterials.GOLD.value().enchantability(),
                ArmorMaterials.LEATHER.value().equipSound(), repair,

                List.of(), resilience.toughness(), resilience.knockbackResistance());
        var entry = Registry.registerReference(Registries.ARMOR_MATERIAL,
                Identifier.of("bewitchment", name), result);
        MATERIALS.put(name, result);
        ENTRIES.put(result, new Registered(entry, harbinger));
        return result;
    }

    private static Ingredient woolRepairIngredient(String name) {
        Identifier id = Identifier.of("bewitchment", name + "_wool");
        if (!Registries.ITEM.containsId(id)) {
            throw new IllegalStateException("Repair item is not registered: " + id);
        }
        return Ingredient.ofItems(Registries.ITEM.get(id));
    }

    public static synchronized ArmorItem createArmor(ArmorMaterial material,
            ArmorItem.Type type, Item.Settings settings) {
        Registered registered = ENTRIES.get(material);
        if (registered == null) {
            throw new IllegalArgumentException("Unrecognized legacy armor material");
        }

        if (!registered.explicitDurability()) settings.maxDamage(type.getMaxDamage(15));
        return new ArmorItem(registered.entry(), type, settings);
    }
}
