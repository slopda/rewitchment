package com.veeda.rewitchment.probe.mixin;

import net.minecraft.block.entity.BedBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BedBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BedBlockEntityRenderer.class)
abstract class BedBlockEntityRendererCompatMixin {
    @Inject(method = "render(Lnet/minecraft/block/entity/BedBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V", at = @At("HEAD"), cancellable = true)
    private void bewitchment$skipCoffin(BedBlockEntity entity, float delta, MatrixStack matrices, VertexConsumerProvider vertices, int light, int overlay, CallbackInfo callback) {
        if (Registries.BLOCK.getId(entity.getCachedState().getBlock()).getNamespace().equals("bewitchment")) callback.cancel();
    }
}
