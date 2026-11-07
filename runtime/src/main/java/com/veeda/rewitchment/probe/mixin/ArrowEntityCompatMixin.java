package com.veeda.rewitchment.probe.mixin;

import com.veeda.rewitchment.probe.LegacyMixinHooks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ArrowEntity.class, priority = 500)
abstract class ArrowEntityCompatMixin {
    @Shadow private void setPotionContents(PotionContentsComponent contents) {}

    public void bewitchment$initFromStack(ItemStack stack) {}

    @Inject(method = "setStack", at = @At("TAIL"))
    private void bewitchment$setStack(ItemStack stack, CallbackInfo callback) {
        if (LegacyMixinHooks.isBrew(stack))
            setPotionContents(LegacyMixinHooks.shortened(stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT), 8));
        bewitchment$initFromStack(stack);
    }
}
