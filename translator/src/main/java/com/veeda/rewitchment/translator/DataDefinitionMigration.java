package com.veeda.rewitchment.translator;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class DataDefinitionMigration {
    private DataDefinitionMigration(){}
    public record Result(Map<String,byte[]> entries,Map<String,Integer> counts){}
    public static Result migrate(Map<String,byte[]> input) {
        var output=new LinkedHashMap<String,byte[]>();var counts=new TreeMap<String,Integer>();
        for(var entry:input.entrySet()) {
            var path=entry.getKey();var bytes=entry.getValue();var parts=path.split("/",4);
            if(parts.length==4&&parts[0].equals("data")&&path.endsWith(".json")&&Set.of("advancement","loot_table").contains(parts[2])) {
                var json=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
                int before=counts.values().stream().mapToInt(Integer::intValue).sum();
                if(parts[2].equals("advancement"))advancement(json,counts);else loot(json,counts);
                if(counts.values().stream().mapToInt(Integer::intValue).sum()!=before)
                    bytes=new GsonBuilder().setPrettyPrinting().create().toJson(json).getBytes(StandardCharsets.UTF_8);
            }
            output.put(path,bytes);
        }
        return new Result(Collections.unmodifiableMap(output),Collections.unmodifiableMap(counts));
    }
    static boolean vanilla(JsonObject object,String key,String name) {
        if(!object.has(key)||!object.get(key).isJsonPrimitive())return false;
        var value=object.get(key).getAsString();return value.equals(name)||value.equals("minecraft:"+name);
    }
    private static void advancement(JsonObject json,Map<String,Integer> counts) {
        if(!json.has("criteria"))return;
        for(var criterion:json.getAsJsonObject("criteria").entrySet()) {
            var value=criterion.getValue().getAsJsonObject();
            if(!vanilla(value,"trigger","inventory_changed")||!value.has("conditions"))continue;
            var conditions=value.getAsJsonObject("conditions");
            if(conditions.has("items"))for(var item:conditions.getAsJsonArray("items"))itemPredicate(item.getAsJsonObject(),counts);
        }
    }
    private static void loot(JsonElement json,Map<String,Integer> counts) {
        if(json.isJsonArray()){json.getAsJsonArray().forEach(value->loot(value,counts));return;}
        if(!json.isJsonObject())return;var object=json.getAsJsonObject();
        if(vanilla(object,"condition","match_tool")&&object.has("predicate"))itemPredicate(object.getAsJsonObject("predicate"),counts);
        if(vanilla(object,"function","looting_enchant")) {
            if(object.has("enchantment"))throw new IllegalArgumentException("Ambiguous legacy looting enchantment");
            object.addProperty("function","minecraft:enchanted_count_increase");object.addProperty("enchantment","minecraft:looting");
            counts.merge("looting_functions",1,Integer::sum);
        }

        for(var entry:new ArrayList<>(object.entrySet()))loot(entry.getValue(),counts);
    }
    private static void itemPredicate(JsonObject predicate,Map<String,Integer> counts) {
        if(predicate.has("tag")) {
            if(predicate.has("items"))throw new IllegalArgumentException("Cannot merge legacy tag and item restrictions");
            predicate.addProperty("items","#"+predicate.remove("tag").getAsString());counts.merge("item_tag_predicates",1,Integer::sum);
        }
        if(predicate.has("enchantments")) {
            var modern=predicate.has("predicates")?predicate.getAsJsonObject("predicates"):new JsonObject();
            if(modern.has("minecraft:enchantments"))throw new IllegalArgumentException("Conflicting enchantment predicates");
            var enchantments=predicate.getAsJsonArray("enchantments");
            for(var value:enchantments) {
                var enchantment=value.getAsJsonObject();
                if(enchantment.has("enchantment")) {
                    if(enchantment.has("enchantments"))throw new IllegalArgumentException("Conflicting enchantment selectors");
                    enchantment.add("enchantments",enchantment.remove("enchantment"));
                }
            }
            predicate.remove("enchantments");modern.add("minecraft:enchantments",enchantments);predicate.add("predicates",modern);
            counts.merge("item_enchantment_predicates",1,Integer::sum);
        }
    }
}
