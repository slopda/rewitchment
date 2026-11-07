package com.veeda.rewitchment.probe;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.player.PlayerEntity;

final class NeoForgeBreathBridge {
    private NeoForgeBreathBridge() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void install() throws ReflectiveOperationException {
        var key = Class.forName("moriyashiine.bewitchment.common.registry.BWComponents").getField("ADDITIONAL_WATER_DATA_COMPONENT").get(null);
        Method get = key.getClass().getMethod("get", Object.class);
        var event = Class.forName("net.neoforged.neoforge.event.entity.living.LivingBreatheEvent");
        Method entity = event.getMethod("getEntity");
        Method setCanBreathe = event.getMethod("setCanBreathe", boolean.class);
        var bus = Class.forName("net.neoforged.neoforge.common.NeoForge").getField("EVENT_BUS").get(null);
        Consumer<Object> listener = fired -> {
            try {
                var living = (LivingEntity) entity.invoke(fired);
                var component = get.invoke(key, living);
                boolean submerged = (boolean) component.getClass().getMethod("isSubmerged").invoke(component);
                boolean exempt = living.canBreatheInWater() || StatusEffectUtil.hasWaterBreathing(living)
                        || living instanceof PlayerEntity player && player.getAbilities().invulnerable;
                if (submerged && !exempt) setCanBreathe.invoke(fired, false);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        };
        bus.getClass().getMethod("addListener", Class.class, Consumer.class).invoke(bus, event, listener);
    }
}
