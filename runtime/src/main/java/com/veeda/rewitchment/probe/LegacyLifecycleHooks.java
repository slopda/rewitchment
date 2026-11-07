package com.veeda.rewitchment.probe;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

final class LegacyLifecycleHooks {
    private static boolean installed;
    private LegacyLifecycleHooks() {}
    static synchronized void install() {
        if(installed)return;installed=true;
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{
            if(entity instanceof ServerPlayerEntity player && player.getVehicle()!=null){
                var id=Registries.ENTITY_TYPE.getId(player.getVehicle().getType());
                if(id.getNamespace().equals("bewitchment") && id.getPath().endsWith("_broom"))player.stopRiding();
            }
        });
    }
}
