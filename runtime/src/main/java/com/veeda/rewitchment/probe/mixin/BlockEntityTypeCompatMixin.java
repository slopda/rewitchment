package com.veeda.rewitchment.probe.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityType.class)
abstract class BlockEntityTypeCompatMixin {
    @Inject(method = "supports", at = @At("HEAD"), cancellable = true)
    private void bewitchment$sharedTypes(BlockState state, CallbackInfoReturnable<Boolean> callback) {
        var type = Registries.BLOCK_ENTITY_TYPE.getId((BlockEntityType<?>) (Object) this);
        if (type != null && type.getNamespace().equals("bewitchment") && Registries.BLOCK.getId(state.getBlock()).getNamespace().equals("bewitchment"))
            callback.setReturnValue(true);
    }
}
