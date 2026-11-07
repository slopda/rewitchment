package com.veeda.rewitchment.startup;
import net.fabricmc.api.ModInitializer;
public final class StartupRuntime implements ModInitializer {
    public void onInitialize(){
        if(!Boolean.getBoolean("rewitchment.startup.active"))return;
        try {String name="com.veeda.rewitchment.probe.RuntimeCompat";var type=Class.forName(name);type.getMethod("onInitialize").invoke(type.getConstructor().newInstance());}
        catch(ReflectiveOperationException error){throw new IllegalStateException("Compatibility runtime could not initialize",error);}
    }
}
