package com.veeda.rewitchment.startup;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.*;
import net.neoforged.fml.loading.FMLPaths;

public final class NeoForgeStartupLocator implements IModFileCandidateLocator {
 @Override public int getPriority(){return 1000;}
 @Override public void findCandidates(ILaunchContext context,IDiscoveryPipeline pipeline){
  try{
   var game=FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();var mods=FMLPaths.MODSDIR.get();
   var source=StartupCache.original(mods);if(source==null){System.out.println("[rewitchment-startup] No original Bewitchment found; compatibility is inactive");return;}
   Class.forName("org.sinytra.connector.locator.ConnectorLocator",false,getClass().getClassLoader());
   var helper=StartupCache.byId(mods,"rewitchment");if(helper==null)throw new IllegalStateException("Startup JAR missing");
   var candidate=StartupCache.prepare(game,helper,true);var runtime=candidate.resolveSibling("runtime.jar");var impersonate=candidate.resolveSibling("impersonate.jar");
   configure(game.resolve("config/connector.json"));

   var dependencySource=StartupCache.byId(mods,"impersonate");
   context.addLocated(source);context.addLocated(dependencySource);
   exclude(source);exclude(dependencySource);
   String previous=System.getProperty("connector.additionalModLocations","");var locations=new LinkedHashSet<String>();
   for(String value:previous.split(","))if(!value.isBlank())locations.add(value);
   for(Path file:List.of(candidate,runtime,impersonate)){
    String value=file.toAbsolutePath().toString();if(value.contains(",")||value.contains("\n")||value.contains("\r"))throw new IllegalStateException("Instance path cannot be represented by Connector");locations.add(value);
   }
   System.setProperty("connector.additionalModLocations",String.join(",",locations));
   System.out.println("[rewitchment-startup] CONNECTOR ATTACHED "+candidate);
  }catch(Exception error){throw new IllegalStateException("Rewitchment automatic Connector preparation failed",error);}
 }
 static void exclude(Path source)throws Exception{
  var type=Class.forName("net.neoforged.fml.loading.ModDirTransformerDiscoverer");var field=type.getDeclaredField("found");field.setAccessible(true);
  @SuppressWarnings("unchecked") var found=(List<Object>)field.get(null);
  var excluded=(List<?>)type.getMethod("allExcluded").invoke(null);
  if(excluded.contains(source))return;
  var named=Class.forName("cpw.mods.modlauncher.api.NamedPath").getConstructor(String.class,Path[].class).newInstance("rewitchment-source",new Path[]{source});found.add(named);
 }
 static void configure(Path path)throws Exception{
  JsonObject object=Files.exists(path)?JsonParser.parseString(Files.readString(path)).getAsJsonObject():new JsonObject();
  String before=object.toString();
  if(!object.has("version"))object.addProperty("version",1);
  if(!object.has("hiddenMods"))object.add("hiddenMods",new JsonArray());
  var aliases=object.has("globalModAliases")?object.getAsJsonObject("globalModAliases"):new JsonObject();
  if(!aliases.has("cloth_config"))aliases.addProperty("cloth_config","cloth-config2");
  if(!aliases.has("playeranimator"))aliases.addProperty("playeranimator","player-animator");object.add("globalModAliases",aliases);object.addProperty("enableMixinSafeguard",false);
  if(!object.toString().equals(before)){
   Files.createDirectories(path.getParent());if(Files.exists(path)&&!Files.exists(path.resolveSibling("connector.before-rewitchment.json")))Files.copy(path,path.resolveSibling("connector.before-rewitchment.json"));
   Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(object)+"\n");
  }
 }
}
