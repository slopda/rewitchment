package com.veeda.rewitchment.probe;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;

final class NeoForgeDamageBridge {
    private NeoForgeDamageBridge() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void install() throws ReflectiveOperationException {
        var handle = Class.forName("moriyashiine.bewitchment.common.registry.BWDamageSources").getMethod("handleDamage", LivingEntity.class, DamageSource.class, float.class);
        var event = Class.forName("net.neoforged.neoforge.event.entity.living.LivingDamageEvent$Pre");
        Method entity = event.getMethod("getEntity");
        Method source = event.getMethod("getSource");
        Method amount = event.getMethod("getNewDamage");
        Method change = event.getMethod("setNewDamage", float.class);
        var bus = Class.forName("net.neoforged.neoforge.common.NeoForge").getField("EVENT_BUS").get(null);
        Consumer<Object> listener = fired -> {
            try {
                change.invoke(fired, handle.invoke(null, entity.invoke(fired), source.invoke(fired), amount.invoke(fired)));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        };
        bus.getClass().getMethod("addListener", Class.class, Consumer.class).invoke(bus, event, listener);
    }
}
