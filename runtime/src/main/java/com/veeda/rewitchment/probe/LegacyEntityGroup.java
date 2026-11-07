package com.veeda.rewitchment.probe;

import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.EntityTypeTags;

public final class LegacyEntityGroup {
    public static final LegacyEntityGroup DEFAULT = new LegacyEntityGroup();
    public static final LegacyEntityGroup UNDEAD = new LegacyEntityGroup();
    public static final LegacyEntityGroup ARTHROPOD = new LegacyEntityGroup();
    public static final LegacyEntityGroup ILLAGER = new LegacyEntityGroup();
    public static final LegacyEntityGroup AQUATIC = new LegacyEntityGroup();

    public interface Provider {
        LegacyEntityGroup method_6046();
    }

    public interface Holder {
        LegacyEntityGroup bewitchment$legacyGroup();
    }

    public LegacyEntityGroup() {}

    public static LegacyEntityGroup of(LivingEntity entity) {
        if (entity instanceof Provider provider) return provider.method_6046();
        if (entity instanceof Holder holder) {
            var injected = holder.bewitchment$legacyGroup();
            if (injected != null) return injected;
        }
        var type = entity.getType();
        if (type.isIn(EntityTypeTags.SENSITIVE_TO_SMITE)) return UNDEAD;
        if (type.isIn(EntityTypeTags.ARTHROPOD)) return ARTHROPOD;
        if (type.isIn(EntityTypeTags.ILLAGER)) return ILLAGER;
        if (type.isIn(EntityTypeTags.AQUATIC)) return AQUATIC;
        return DEFAULT;
    }
}
