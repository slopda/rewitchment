package com.veeda.rewitchment.probe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;

public final class LegacyPotionContents {
    private LegacyPotionContents() {}

    private static PotionContentsComponent contents(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.POTION_CONTENTS,
                PotionContentsComponent.DEFAULT);
    }

    public static List<StatusEffectInstance> getCustomPotionEffects(ItemStack stack) {
        return new ArrayList<>(contents(stack).customEffects());
    }

    public static List<StatusEffectInstance> getPotionEffects(ItemStack stack) {
        var effects = new ArrayList<StatusEffectInstance>();
        contents(stack).getEffects().forEach(effects::add);
        return effects;
    }

    public static ItemStack setCustomPotionEffects(
            ItemStack stack, Collection<StatusEffectInstance> newEffects) {
        if (newEffects.isEmpty()) return stack;
        PotionContentsComponent current = contents(stack);
        var effects = new ArrayList<>(current.customEffects());
        effects.addAll(newEffects);
        stack.set(DataComponentTypes.POTION_CONTENTS,
                new PotionContentsComponent(current.potion(), current.customColor(),
                        List.copyOf(effects)));
        return stack;
    }

    public static int getColor(ItemStack stack) {
        return contents(stack).getColor();
    }

    public static int getColor(Collection<StatusEffectInstance> effects) {
        return PotionContentsComponent.getColor(effects);
    }

    public static ItemStack setPotion(ItemStack stack, Potion potion) {
        RegistryEntry<Potion> registered = Registries.POTION.getEntry(potion);
        stack.set(DataComponentTypes.POTION_CONTENTS, contents(stack).with(registered));
        return stack;
    }

    public static Potion getPotion(ItemStack stack) {
        return contents(stack).potion().map(RegistryEntry::value).orElse(null);
    }

    public static void setCustomPotionColor(ItemStack stack, String key, int argb) {
        if (!"CustomPotionColor".equals(key)) {
            throw new IllegalArgumentException("Expected the exact legacy CustomPotionColor key");
        }
        PotionContentsComponent current = contents(stack);
        stack.set(DataComponentTypes.POTION_CONTENTS,
                new PotionContentsComponent(current.potion(), Optional.of(argb),
                        current.customEffects()));
    }
}
