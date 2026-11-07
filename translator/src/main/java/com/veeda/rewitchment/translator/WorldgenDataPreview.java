package com.veeda.rewitchment.translator;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

public final class WorldgenDataPreview {
    public static void main(String[] args) throws Exception {
        if(args.length!=0)throw new IllegalArgumentException("No arguments supported");
        Path root=Path.of("..").toAbsolutePath().normalize();
        Path original=root.resolve("../original/bewitchment-1.20-10.jar").normalize();
        var hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(original)));
        if(!hash.equals(TranslationLab.RELEASE_SHA1))throw new IllegalStateException("Unexpected original");
        var entries=new LinkedHashMap<String,byte[]>();
        try(var jar=new ZipFile(root.resolve("reports/preview/bewitchment-v31-local-only.jar").toFile())) {
            for(var e:Collections.list(jar.entries()))if(!e.isDirectory())
                try(var in=jar.getInputStream(e)){entries.put(e.getName(),in.readAllBytes());}
        }
        var result=WorldgenProviderMigration.migrate(entries);
        if(!result.counts().equals(Map.of("uniform_int_provider_payloads",1)))
            throw new IllegalStateException("Unexpected worldgen corpus: "+result.counts());
        var destination=root.resolve("reports/preview/bewitchment-v32-local-only.jar");
        try(var out=new ZipOutputStream(Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW))) {
            for(var e:result.entries().entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));out.write(e.getValue());out.closeEntry();
            }
        }
        var report=new LinkedHashMap<String,Object>();report.put("original_sha1",hash);
        report.put("worldgen_batch",result.counts());report.put("installable_or_playable",false);
        report.put("classfiles_identical_to_v31",true);
        report.put("limits",List.of("Offline discovery only; no loader activation", "Codec and predicate tests use explicit registry fixtures", "Tag binding and registry creation use explicit object fixtures; full original initialization remains unverified"));
        Files.writeString(root.resolve("reports/translation-prototype-v32.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        System.out.println("V32 private definition diagnostic: "+result.counts());
    }
}
