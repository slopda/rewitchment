package com.veeda.rewitchment.probe.mixin;

import com.veeda.rewitchment.probe.LegacyMixinHooks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
abstract class InGameHudCompatMixin {
    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
    private void bewitchment$hideFood(DrawContext context, PlayerEntity player, int top, int right, CallbackInfo callback) {
        if (LegacyMixinHooks.isVampire(player)) callback.cancel();
    }
}
