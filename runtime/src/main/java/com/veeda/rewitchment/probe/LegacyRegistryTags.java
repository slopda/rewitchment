package com.veeda.rewitchment.probe;

import java.util.*;
import java.io.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.registry.tag.TagFile;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

public final class LegacyRegistryTags {
    private LegacyRegistryTags(){}
    public record Binding(int tags,int memberReferences){}

    public static <T> Binding loadAndBind(Registry<T> registry,ResourceManager resources) {
        var directory=RegistryKeys.getTagPath(registry.getKey());
        var expected=new HashSet<Identifier>();
        for(var file:resources.findAllResources(directory,id->id.getPath().endsWith(".json")).entrySet()) {
            var path=file.getKey().getPath();
            expected.add(Identifier.of(file.getKey().getNamespace(),path.substring(directory.length()+1,path.length()-5)));
            for(var resource:file.getValue())try(var reader=resource.getReader()) {
                TagFile.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseReader(reader)).getOrThrow();
            } catch(IOException error){throw new UncheckedIOException("Cannot read tag "+file.getKey(),error);}
        }
        var loader=new TagGroupLoader<RegistryEntry<T>>(registry::getEntry,directory);
        var definitions=loader.loadTags(resources);
        var resolved=loader.buildGroup(definitions);
        var missing=new TreeSet<>(expected);missing.removeAll(resolved.keySet());
        if(!missing.isEmpty())throw new IllegalStateException("Unresolved required tags in "+registry.getKey().getValue()+": "+missing);
        var bindings=new HashMap<TagKey<T>,List<RegistryEntry<T>>>();
        resolved.forEach((id,members)->bindings.put(TagKey.of(registry.getKey(),id),List.copyOf(members)));
        registry.clearTags();registry.populateTags(bindings);
        return new Binding(bindings.size(),bindings.values().stream().mapToInt(List::size).sum());
    }
}
