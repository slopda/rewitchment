package com.veeda.rewitchment.probe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class LegacyMixinHooks {
    private static MethodHandle vampire;

    private LegacyMixinHooks() {}

    public interface EffectRemoval {
        void bewitchment$onRemoved(LivingEntity entity, AttributeContainer attributes, int amplifier);
    }

    public static boolean isVampire(Entity entity) {
        try {
            if (vampire == null) vampire = MethodHandles.publicLookup().findStatic(
                    Class.forName("moriyashiine.bewitchment.api.BewitchmentAPI"), "isVampire",
                    MethodType.methodType(boolean.class, Entity.class, boolean.class));
            return (boolean) vampire.invoke(entity, true);
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (Throwable failure) {
            throw new IllegalStateException(failure);
        }
    }

    public static boolean isBrew(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getBoolean("BewitchmentBrew");
    }

    public static boolean convertsPotions(ItemStack ingredient) {
        return ingredient.isOf(Items.GUNPOWDER) || ingredient.isOf(Items.DRAGON_BREATH);
    }

    public static PotionContentsComponent shortened(PotionContentsComponent contents, int divisor) {
        var effects = new ArrayList<StatusEffectInstance>();
        for (var effect : contents.customEffects())
            effects.add(new StatusEffectInstance(effect.getEffectType(), effect.getDuration() / divisor, effect.getAmplifier(), effect.isAmbient(), effect.shouldShowParticles()));
        return new PotionContentsComponent(contents.potion(), contents.customColor(), effects);
    }
}
