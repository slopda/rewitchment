package com.veeda.rewitchment.probe;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;

public final class LegacyStatusEffects {
    private LegacyStatusEffects() {}

    private static RegistryEntry<StatusEffect> entry(StatusEffect effect) {
        return Registries.STATUS_EFFECT.getEntry(effect);
    }

    public static StatusEffectInstance create(StatusEffect effect, int duration) {
        return new StatusEffectInstance(entry(effect), duration);
    }

    public static StatusEffectInstance create(StatusEffect effect, int duration, int amplifier) {
        return new StatusEffectInstance(entry(effect), duration, amplifier);
    }

    public static StatusEffectInstance create(StatusEffect effect, int duration, int amplifier,
                                              boolean ambient, boolean showParticles) {
        return new StatusEffectInstance(entry(effect), duration, amplifier, ambient, showParticles);
    }

    public static StatusEffectInstance create(StatusEffect effect, int duration, int amplifier,
                                              boolean ambient, boolean showParticles,
                                              boolean showIcon) {
        return new StatusEffectInstance(entry(effect), duration, amplifier, ambient,
                showParticles, showIcon);
    }

    public static StatusEffect getEffectType(StatusEffectInstance instance) {
        return instance.getEffectType().value();
    }

    public static boolean hasStatusEffect(LivingEntity entity, StatusEffect effect) {
        return entity.hasStatusEffect(entry(effect));
    }

    public static StatusEffectInstance getStatusEffect(LivingEntity entity, StatusEffect effect) {
        return entity.getStatusEffect(entry(effect));
    }

    public static boolean removeStatusEffect(LivingEntity entity, StatusEffect effect) {
        return entity.removeStatusEffect(entry(effect));
    }
}
