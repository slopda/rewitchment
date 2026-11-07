package com.veeda.rewitchment.probe;

import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.registry.Registries;

public final class LegacyEntityAttributes {
    private LegacyEntityAttributes() {}

    public static DefaultAttributeContainer.Builder add(
            DefaultAttributeContainer.Builder builder, EntityAttribute attribute, double value) {
        return builder.add(Registries.ATTRIBUTE.getEntry(attribute), value);
    }
}
