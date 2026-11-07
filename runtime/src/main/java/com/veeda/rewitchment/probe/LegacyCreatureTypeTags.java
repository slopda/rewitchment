package com.veeda.rewitchment.probe;

import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.EntityTypeTags;

public final class LegacyCreatureTypeTags {
    private LegacyCreatureTypeTags() {}

    public static boolean isArthropod(LivingEntity creature) {
        return creature.getType().isIn(EntityTypeTags.ARTHROPOD);
    }

    public static boolean isIllager(LivingEntity creature) {
        return creature.getType().isIn(EntityTypeTags.ILLAGER);
    }
}
