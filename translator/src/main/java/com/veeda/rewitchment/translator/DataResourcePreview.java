package com.veeda.rewitchment.translator;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

public final class DataResourcePreview {
    public static void main(String[] args) throws Exception {
        if(args.length!=0)throw new IllegalArgumentException("No arguments supported");
        Path root=Path.of("..").toAbsolutePath().normalize();
        Path original=root.resolve("../original/bewitchment-1.20-10.jar").normalize();
        var hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(original)));
        if(!hash.equals(TranslationLab.RELEASE_SHA1))throw new IllegalStateException("Unexpected original");
        var entries=new LinkedHashMap<String,byte[]>();
        try(var jar=new ZipFile(root.resolve("reports/preview/bewitchment-v29-local-only.jar").toFile())) {
            for(var e:Collections.list(jar.entries()))if(!e.isDirectory())
                try(var in=jar.getInputStream(e)){entries.put(e.getName(),in.readAllBytes());}
        }
        var result=DataResourceMigration.migrate(entries);
        if(!result.counts().equals(Map.of("paths_recipes",347,"paths_advancements",232,
                "paths_loot_tables",344,"paths_tags",110,"vanilla_recipe_results",224)))
            throw new IllegalStateException("Unexpected resource corpus: "+result.counts());
        var destination=root.resolve("reports/preview/bewitchment-v30-local-only.jar");
        try(var out=new ZipOutputStream(Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW))) {
            for(var e:result.entries().entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));out.write(e.getValue());out.closeEntry();
            }
        }
        var report=new LinkedHashMap<String,Object>();report.put("original_sha1",hash);
        report.put("resource_batch",result.counts());report.put("installable_or_playable",false);
        report.put("classfiles_identical_to_v29",true);
        report.put("limits",List.of("Offline discovery only; no loader activation", "Loot/advancement paths only; schemas not migrated", "Registry/tag binding and original bootstrap remain unverified"));
        Files.writeString(root.resolve("reports/translation-prototype-v30.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        System.out.println("V30 private resource diagnostic: "+result.counts());
    }
}
