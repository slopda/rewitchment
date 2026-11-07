package com.veeda.rewitchment.probe;

import java.util.Objects;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.item.Item;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
import net.minecraft.block.Block;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public final class LegacyWeaponComponents {
    private static final ToolMaterial SILVER = new ToolMaterial() {
        public int getDurability() { return ToolMaterials.IRON.getDurability(); }
        public float getMiningSpeedMultiplier() { return ToolMaterials.GOLD.getMiningSpeedMultiplier(); }
        public float getAttackDamage() { return ToolMaterials.IRON.getAttackDamage(); }
        public TagKey<Block> getInverseTag() { return ToolMaterials.IRON.getInverseTag(); }
        public int getEnchantability() { return ToolMaterials.GOLD.getEnchantability(); }
        public Ingredient getRepairIngredient() {
            Identifier id = Identifier.of("bewitchment", "silver_ingot");
            if (!Registries.ITEM.containsId(id)) {
                throw new IllegalStateException("Repair item is not registered: " + id);
            }
            return Ingredient.ofItems(Registries.ITEM.get(id));
        }
    };

    private LegacyWeaponComponents() {}
    public static ToolMaterial silver() { return SILVER; }

    private record DurabilityMaterial(ToolMaterial delegate, int durability) implements ToolMaterial {
        public int getDurability() { return durability; }
        public float getMiningSpeedMultiplier() { return delegate.getMiningSpeedMultiplier(); }
        public float getAttackDamage() { return delegate.getAttackDamage(); }
        public TagKey<Block> getInverseTag() { return delegate.getInverseTag(); }
        public int getEnchantability() { return delegate.getEnchantability(); }
        public Ingredient getRepairIngredient() { return delegate.getRepairIngredient(); }
    }

    public static ToolMaterial withDurability(ToolMaterial material, int durability) {
        Objects.requireNonNull(material, "Original material");
        if (durability <= 0) throw new IllegalArgumentException("Nonpositive durability");
        return material.getDurability() == durability ? material : new DurabilityMaterial(material, durability);
    }

    public static Item.Settings swordSettings(ToolMaterial material, int damage,
            float speed, Item.Settings settings, EntityAttributeModifier reach) {
        Objects.requireNonNull(reach, "Original reach modifier");
        return settings.attributeModifiers(SwordItem.createAttributeModifiers(material, damage, speed)
                .with(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE, reach, AttributeModifierSlot.MAINHAND));
    }

    public static Item.Settings miningSettings(ToolMaterial material, float damage,
            float speed, Item.Settings settings) {
        return settings.attributeModifiers(MiningToolItem.createAttributeModifiers(material, damage, speed));
    }

    public static float miningSpeed(ToolItem item) {
        return item.getMaterial().getMiningSpeedMultiplier();
    }
}
