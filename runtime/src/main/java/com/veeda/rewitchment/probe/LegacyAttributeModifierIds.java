package com.veeda.rewitchment.probe;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.util.Identifier;

public final class LegacyAttributeModifierIds {
    private LegacyAttributeModifierIds() {}

    public static EntityAttributeModifier create(UUID id, String originalName,
                                                 double value,
                                                 EntityAttributeModifier.Operation operation) {
        Objects.requireNonNull(id, "Original attribute modifier UUID");
        Objects.requireNonNull(originalName, "Original attribute modifier name");
        Objects.requireNonNull(operation, "Attribute modifier operation");

        Identifier key = Identifier.of("bewitchment", "legacy_modifier/" + id);
        return new EntityAttributeModifier(key, value, operation);
    }
}
