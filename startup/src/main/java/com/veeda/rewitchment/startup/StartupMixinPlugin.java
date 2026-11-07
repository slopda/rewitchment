package com.veeda.rewitchment.startup;
import java.util.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
public final class StartupMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String mixinPackage){}
 public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String mixin){return Boolean.getBoolean("rewitchment.startup.active");}
 public void acceptTargets(Set<String> mine,Set<String> others){}
 public List<String> getMixins(){return null;}
 public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
 public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
