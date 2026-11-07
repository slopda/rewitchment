package com.veeda.rewitchment.startup;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.*;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.metadata.*;

public final class FabricStartupAdapter implements LanguageAdapter {
    public FabricStartupAdapter() {
        try {
            var loader=FabricLoader.getInstance();
            var slot=loader.getModContainer("bewitchment").orElseThrow();
            var marker=slot.getMetadata().getCustomValue("rewitchment:startup_slot");
            if(marker==null || !marker.getAsBoolean())throw new IllegalStateException("Expected the independent startup slot, not an already converted Bewitchment installation");
            var jar=Path.of(FabricStartupAdapter.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            var generated=StartupCache.prepare(loader.getGameDir(),jar);
            if(generated==null){disable(loader);System.out.println("[rewitchment-startup] No original Bewitchment found; compatibility is inactive; rewitchment="+loader.isModLoaded("rewitchment")+" bewitchment="+loader.isModLoaded("bewitchment"));return;}
            var config=loader.getConfigDir();
            LoaderModMetadata metadata;
            try(var zip=new ZipFile(generated.toFile())){
                metadata=ModMetadataParser.parseMetadata(zip.getInputStream(zip.getEntry("fabric.mod.json")),generated.toString(),List.of(),new VersionOverrides(),new DependencyOverrides(config),false);
            }
            var missing=new ArrayList<String>();
            for(var dependency:metadata.getDependencies())if(dependency.getKind()==net.fabricmc.loader.api.metadata.ModDependency.Kind.DEPENDS){
                var installed=loader.getModContainer(dependency.getModId());
                if(installed.isEmpty()||!dependency.matches(installed.get().getMetadata().getVersion()))missing.add(dependency.toString());
            }
            if(!missing.isEmpty())throw new IllegalStateException("Missing or incompatible Bewitchment dependencies: "+missing);

            set(slot,"info",metadata);set(slot,"codeSourcePaths",List.of(generated));set(slot,"roots",null);
            FabricLauncherBase.getLauncher().addToClassPath(generated);
            FabricLauncherBase.getLauncher().addToClassPath(generated.resolveSibling("runtime.jar"));
            System.setProperty("rewitchment.startup.active","true");
            System.out.println("[rewitchment-startup] ATTACHED "+generated);
        } catch(Exception error){throw new IllegalStateException("Rewitchment automatic preparation failed",error);}
    }
    private static void disable(FabricLoader loader)throws Exception {
        var field=loader.getClass().getDeclaredField("mods");field.setAccessible(true);
        @SuppressWarnings("unchecked") var mods=(List<ModContainer>)field.get(loader);
        field.set(loader,new ArrayList<>(mods.stream().filter(mod->!Set.of("rewitchment","bewitchment").contains(mod.getMetadata().getId())).toList()));
        var mapField=loader.getClass().getDeclaredField("modMap");mapField.setAccessible(true);
        var map=(Map<?,?>)mapField.get(loader);map.remove("rewitchment");map.remove("bewitchment");
    }
    private static void set(Object object,String name,Object value)throws Exception {
        var field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    public <T>T create(ModContainer mod,String value,Class<T> type)throws LanguageAdapterException {
        return LanguageAdapter.getDefault().create(mod,value,type);
    }
}
