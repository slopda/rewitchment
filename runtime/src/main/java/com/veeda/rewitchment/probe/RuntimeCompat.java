package com.veeda.rewitchment.probe;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class RuntimeCompat implements ModInitializer {
    @Override public void onInitialize() { LegacyLifecycleHooks.install(); installBridges(); }
    static void installBridges() {
        if (!FabricLoader.getInstance().isModLoaded("neoforge")) return;
        try { NeoForgeBreathBridge.install(); NeoForgeDamageBridge.install(); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("NeoForge compatibility bridges failed", failure); }
    }
}
