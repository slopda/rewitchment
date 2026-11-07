package com.veeda.rewitchment.translator;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class DataResourceMigration {
    private DataResourceMigration() {}
    private static final Map<String,String> DIRECTORIES=Map.of(
        "recipes","recipe","advancements","advancement","loot_tables","loot_table");
    private static final Map<String,String> TAGS=Map.of(
        "items","item","blocks","block","entity_types","entity_type");
    private static final Set<String> VANILLA_RESULTS=Set.of("crafting_shaped","crafting_shapeless",
        "smelting","blasting","smoking","campfire_cooking");
    public record Result(Map<String,byte[]> entries, Map<String,Integer> counts) {}

    public static String path(String source) {
        var parts=source.split("/",-1);
        if(parts.length<4 || !parts[0].equals("data"))return source;
        parts[2]=DIRECTORIES.getOrDefault(parts[2],parts[2]);
        if(parts[2].equals("tags") && parts.length>=5)parts[3]=TAGS.getOrDefault(parts[3],parts[3]);
        return String.join("/",parts);
    }
    public static Result migrate(Map<String,byte[]> source) {
        var output=new LinkedHashMap<String,byte[]>();var counts=new TreeMap<String,Integer>();
        for(var entry:source.entrySet()) {
            var name=entry.getKey();var target=path(name);var bytes=entry.getValue();
            if(!name.equals(target))counts.merge("paths_"+name.split("/")[2],1,Integer::sum);
            if(name.startsWith("data/") && name.split("/",4).length==4 && name.split("/",4)[2].equals("recipes") && name.endsWith(".json")) {
                var json=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
                var type=json.get("type").getAsString();
                var vanilla=type.startsWith("minecraft:")?type.substring(10):type;
                if(VANILLA_RESULTS.contains(vanilla)) {
                    var result=json.get("result");
                    if(result.isJsonPrimitive() && result.getAsJsonPrimitive().isString()) {
                        var replacement=new JsonObject();replacement.add("id",result);json.add("result",replacement);
                    } else if(result.isJsonObject() && result.getAsJsonObject().has("item")) {
                        var object=result.getAsJsonObject();
                        if(object.has("id") || object.has("data") || object.has("nbt"))
                            throw new IllegalArgumentException("Ambiguous/unsupported legacy result: "+name);
                        object.add("id",object.remove("item"));
                    } else throw new IllegalArgumentException("Expected legacy vanilla result: "+name);
                    bytes=new GsonBuilder().setPrettyPrinting().create().toJson(json).getBytes(StandardCharsets.UTF_8);
                    counts.merge("vanilla_recipe_results",1,Integer::sum);
                }
            }
            if(output.putIfAbsent(target,bytes)!=null)throw new IllegalArgumentException("Resource collision: "+target);
        }
        return new Result(Collections.unmodifiableMap(output),Collections.unmodifiableMap(counts));
    }
}
