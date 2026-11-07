package com.veeda.rewitchment.probe.mixin;

import com.veeda.rewitchment.probe.LegacyEntityGroup;
import com.veeda.rewitchment.probe.LegacyMixinHooks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntity.class, priority = 500)
abstract class LivingEntityCompatMixin extends Entity implements LegacyEntityGroup.Holder {
    LivingEntityCompatMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Shadow public abstract AttributeContainer getAttributes();
    @Shadow public abstract double getAttributeValue(RegistryEntry<EntityAttribute> attribute);

    @Override public LegacyEntityGroup bewitchment$legacyGroup() {
        return null;
    }

    @Inject(method = "hasInvertedHealingAndHarm", at = @At("RETURN"), cancellable = true)
    private void bewitchment$undeadPlayers(CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ() && (Object) this instanceof PlayerEntity && LegacyEntityGroup.of((LivingEntity) (Object) this) == LegacyEntityGroup.UNDEAD)
            callback.setReturnValue(true);
    }

    @Inject(method = "onStatusEffectRemoved", at = @At("TAIL"))
    private void bewitchment$removed(StatusEffectInstance effect, CallbackInfo callback) {
        if (!getWorld().isClient && effect.getEffectType().value() instanceof LegacyMixinHooks.EffectRemoval removal)
            removal.bewitchment$onRemoved((LivingEntity) (Object) this, getAttributes(), effect.getAmplifier());
    }

    @Redirect(method = "setOnFireForTicks", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getAttributeValue(Lnet/minecraft/registry/entry/RegistryEntry;)D"))
    private double bewitchment$burnTime(LivingEntity entity, RegistryEntry<EntityAttribute> attribute) {
        return LegacyMixinHooks.isVampire(entity) ? 1D : getAttributeValue(attribute);
    }
}
