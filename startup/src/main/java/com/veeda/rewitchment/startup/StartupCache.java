package com.veeda.rewitchment.startup;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import com.google.gson.*;

public final class StartupCache {
 public static final String ORIGINAL_SHA1="11ac0992d1e54af8515b6c339d0f6bfc18b4d2d0";
 public static String hash(Path p,String algorithm)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(Files.readAllBytes(p)));}
 public static Path original(Path mods)throws Exception{
  Path found=null;
  if(!Files.isDirectory(mods))return null;
  try(var paths=Files.list(mods)){
   for(var path:paths.filter(p->p.getFileName().toString().endsWith(".jar")).sorted().toList()){
    try(var zip=new ZipFile(path.toFile())){
     var entry=zip.getEntry("fabric.mod.json");if(entry==null)continue;
     var meta=JsonParser.parseString(new String(zip.getInputStream(entry).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
     if(!"bewitchment".equals(meta.get("id").getAsString()))continue;
     if(!hash(path,"SHA-1").equals(ORIGINAL_SHA1))throw new IllegalStateException("Unsupported Bewitchment JAR: use unmodified 1.20-10");
     if(found!=null)throw new IllegalStateException("Multiple original Bewitchment JARs; keep one copy");found=path;
    }
   }
  }
  return found;
 }
 public static Path byId(Path mods,String id)throws Exception {
  Path found=null;
  try(var paths=Files.list(mods)){
   for(var path:paths.filter(p->p.getFileName().toString().endsWith(".jar")).sorted().toList())try(var zip=new ZipFile(path.toFile())){
    var entry=zip.getEntry("fabric.mod.json");if(entry==null)continue;
    var metadata=JsonParser.parseString(new String(zip.getInputStream(entry).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
    if(!id.equals(metadata.get("id").getAsString()))continue;
    if(found!=null)throw new IllegalStateException("Multiple "+id+" JARs");found=path;
   }
  }
  return found;
 }
 static List<String> outputs(boolean connector){return connector?List.of("bewitchment.jar","runtime.jar","impersonate.jar"):List.of("bewitchment.jar","runtime.jar");}
 static boolean validCache(Path cache,boolean connector)throws Exception {
  var manifest=cache.resolve("checksums.json");if(!Files.isRegularFile(manifest))return false;
  try{
   var hashes=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
   if(!hashes.keySet().equals(new HashSet<>(outputs(connector))))return false;
   for(String name:outputs(connector))if(!Files.isRegularFile(cache.resolve(name))||!hash(cache.resolve(name),"SHA-256").equals(hashes.get(name).getAsString()))return false;
   return true;
  }catch(java.io.IOException|RuntimeException invalid){return false;}
 }
 public static Path prepare(Path game,Path helper)throws Exception{return prepare(game,helper,false);}
 public static Path prepare(Path game,Path helper,boolean connector)throws Exception{
  var source=original(game.resolve("mods"));if(source==null)return null;
  Path impersonate=connector?byId(game.resolve("mods"),"impersonate"):null;
  if(connector&&(impersonate==null||!hash(impersonate,"SHA-256").equals("0c97aa58c99586b6bda9faa115d971ee5ff615b627af96f404bc14ea15a3352d")))throw new IllegalStateException("Connector needs unmodified Impersonate 3.2.1 standalone");
  String key=hash(helper,"SHA-256")+(connector?"-connector":"-fabric");var cache=game.resolve(".rewitchment/cache").resolve(key);Files.createDirectories(cache.getParent());
  var target=cache.resolve("bewitchment.jar");
  if(validCache(cache,connector)){System.out.println("[rewitchment-startup] CACHE HIT");return target;}
  if(Files.exists(cache)){
   var invalid=cache.resolveSibling("invalid-"+key+"-"+UUID.randomUUID());
   var boundary=game.toAbsolutePath().normalize().resolve(".rewitchment/cache");
   if(!cache.toAbsolutePath().normalize().startsWith(boundary)||!invalid.toAbsolutePath().normalize().startsWith(boundary))throw new IllegalStateException("Unsafe cache quarantine");
   Files.move(cache,invalid);System.out.println("[rewitchment-startup] Rebuilding damaged cache");
  }
  var work=Files.createTempDirectory(cache.getParent(),"prepare-");
  try{
   for(String directory:List.of("original","compat/translator","compat/reports/preview","compat/fabric/build/libs","compat/test-libs","result"))Files.createDirectories(work.resolve(directory));
   Files.copy(source,work.resolve("original/bewitchment-1.20-10.jar"));
   try(var input=StartupCache.class.getResourceAsStream("/rewitchment-startup/runtime.jar")){if(input==null)throw new IllegalStateException("Runtime resource absent");Files.copy(input,work.resolve("compat/fabric/build/libs/rewitchment-0.1.0.jar"));}
   var engine=work.resolve("engine.jar");
   try(var input=StartupCache.class.getResourceAsStream("/rewitchment-startup/engine.jar")){if(input==null)throw new IllegalStateException("Conversion engine absent");Files.copy(input,engine);}
   var javaExecutable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"java.exe":"java");
   var stages=new ArrayList<>(List.of("DataRecipePreview","DataResourcePreview","DataDefinitionPreview","WorldgenDataPreview","BlockStartupPreview","CompletionPreview"));
   if(connector){
    Files.copy(impersonate,work.resolve("compat/test-libs/impersonate-3.2.1-standalone.jar"));
    try(var input=StartupCache.class.getResourceAsStream("/rewitchment-startup/connector-names.tsv")){Files.copy(input,work.resolve("compat/test-libs/intermediary-mojang-1.21.1.tsv"));}
    stages.add("ConnectorPreview");
   }
   for(String stage:stages){
    var log=work.resolve(stage+".log");var builder=new ProcessBuilder(javaExecutable.toString(),"-Xmx1G","-cp",engine.toAbsolutePath().toString(),"com.veeda.rewitchment.translator."+stage).directory(work.resolve("compat/translator").toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
    var process=builder.start();if(!process.waitFor(120,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly().waitFor();throw new IllegalStateException("Conversion timed out: "+stage);}
    if(process.exitValue()!=0)throw new IllegalStateException("Conversion failed: "+stage+"; "+Files.readString(log));
   }
   if(!hash(source,"SHA-1").equals(ORIGINAL_SHA1))throw new IllegalStateException("Original changed during preparation");
   Files.copy(work.resolve(connector?"compat/reports/preview/bewitchment-v35-neoforge-local-only.jar":"compat/reports/preview/bewitchment-v34-local-only.jar"),work.resolve("result/bewitchment.jar"));
   Files.copy(work.resolve(connector?"compat/reports/preview/rewitchment-neoforge.jar":"compat/fabric/build/libs/rewitchment-0.1.0.jar"),work.resolve("result/runtime.jar"));
   if(connector)Files.copy(work.resolve("compat/reports/preview/impersonate-3.2.1-standalone.jar"),work.resolve("result/impersonate.jar"));var checksums=new JsonObject();for(String name:outputs(connector))checksums.addProperty(name,hash(work.resolve("result").resolve(name),"SHA-256"));Files.writeString(work.resolve("result/checksums.json"),checksums.toString()+"\n");
   Files.move(work.resolve("result"),cache);System.out.println("[rewitchment-startup] CACHE BUILT");return target;
  } finally{
   if(!work.toAbsolutePath().normalize().startsWith(game.toAbsolutePath().normalize().resolve(".rewitchment/cache")))throw new IllegalStateException("Unsafe temporary directory");
   try(var paths=Files.walk(work)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
  }
 }
}
