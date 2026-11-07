package com.veeda.rewitchment.probe.mixin;

import com.veeda.rewitchment.probe.LegacyMixinHooks;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PotionEntity.class)
abstract class PotionEntityCompatMixin extends ThrownItemEntity {
    PotionEntityCompatMixin(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    @ModifyArg(method = "applyLingeringPotion", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/AreaEffectCloudEntity;setPotionContents(Lnet/minecraft/component/type/PotionContentsComponent;)V"))
    private PotionContentsComponent bewitchment$lingering(PotionContentsComponent contents) {
        return LegacyMixinHooks.isBrew(getStack()) ? LegacyMixinHooks.shortened(contents, 4) : contents;
    }
}
