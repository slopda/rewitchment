package com.veeda.rewitchment.translator;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class WorldgenProviderMigration {
    private WorldgenProviderMigration(){}
    public record Result(Map<String,byte[]> entries,Map<String,Integer> counts){}
    public static Result migrate(Map<String,byte[]> input) {
        var output=new LinkedHashMap<String,byte[]>();int count=0;
        for(var entry:input.entrySet()) {
            var path=entry.getKey();var bytes=entry.getValue();var parts=path.split("/",3);int changed=0;
            if(parts.length==3&&parts[0].equals("data")&&parts[2].startsWith("worldgen/placed_feature/")&&path.endsWith(".json")) {
                var json=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
                for(var element:json.getAsJsonArray("placement")) {
                    var modifier=element.getAsJsonObject();if(!vanilla(modifier,"type","count")||!modifier.get("count").isJsonObject())continue;
                    var provider=modifier.getAsJsonObject("count");if(!vanilla(provider,"type","uniform")||!provider.has("value"))continue;
                    var values=provider.getAsJsonObject("value");
                    if(provider.size()!=2||!values.keySet().equals(Set.of("min_inclusive","max_inclusive")))
                        throw new IllegalArgumentException("Ambiguous legacy uniform provider: "+path);
                    provider.remove("value");values.entrySet().forEach(value->provider.add(value.getKey(),value.getValue()));changed++;
                }
                if(changed>0)bytes=new GsonBuilder().setPrettyPrinting().create().toJson(json).getBytes(StandardCharsets.UTF_8);
            }
            output.put(path,bytes);count+=changed;
        }
        return new Result(Collections.unmodifiableMap(output),count==0?Map.of():Map.of("uniform_int_provider_payloads",count));
    }
    private static boolean vanilla(JsonObject object,String key,String name) {
        if(!object.has(key))return false;var value=object.get(key).getAsString();return value.equals(name)||value.equals("minecraft:"+name);
    }
}
