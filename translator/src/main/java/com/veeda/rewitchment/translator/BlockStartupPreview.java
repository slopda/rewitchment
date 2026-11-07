package com.veeda.rewitchment.translator;

import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public final class BlockStartupPreview {

    static final String MEAT_TAG = "data/minecraft/tags/item/meat.json";
    static final String MEAT_JSON = "{\"replace\":false,\"values\":[\"bewitchment:demon_heart\",\"bewitchment:grotesque_stew\"]}\n";

    public static Map<String, byte[]> transform(Map<String, byte[]> entries, Map<String, Integer> counts, List<String> omitted) {
        var nodes = new LinkedHashMap<String, ClassNode>();
        for (var entry : entries.entrySet()) if (entry.getKey().endsWith(".class")) {
            var node = new ClassNode();
            new ClassReader(entry.getValue()).accept(node, 0);
            nodes.put(entry.getKey(), node);
        }
        var scan = BlockStartupAdapter.scan(nodes.values());
        var output = new LinkedHashMap<String, byte[]>();
        for (var entry : entries.entrySet()) {
            var name = entry.getKey();
            if (!name.endsWith(".class")) {
                output.put(name, entry.getValue());
                continue;
            }
            var owner = name.substring(0, name.length() - 6);
            if (BlockStartupAdapter.SAPLINGS.containsKey(owner)) {
                omitted.add(owner);
                continue;
            }
            var result = BlockStartupAdapter.rewrite(nodes.get(name), scan);
            if (result.counts().isEmpty()) {
                output.put(name, entry.getValue());
                continue;
            }
            result.counts().forEach((key, value) -> counts.merge(key, value, Integer::sum));
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            result.node().accept(writer);
            output.put(name, writer.toByteArray());
        }
        if (output.containsKey(MEAT_TAG)) throw new IllegalStateException("Unexpected shipped meat tag");
        output.put(MEAT_TAG, MEAT_JSON.getBytes(StandardCharsets.UTF_8));
        return output;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("No arguments supported");
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path original = root.resolve("../original/bewitchment-1.20-10.jar").normalize();
        var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(original)));
        if (!hash.equals(TranslationLab.RELEASE_SHA1)) throw new IllegalStateException("Unexpected original");
        var entries = new LinkedHashMap<String, byte[]>();
        try (var jar = new ZipFile(root.resolve("reports/preview/bewitchment-v32-local-only.jar").toFile())) {
            for (var entry : Collections.list(jar.entries())) if (!entry.isDirectory())
                try (var input = jar.getInputStream(entry)) {
                    entries.put(entry.getName(), input.readAllBytes());
                }
        }
        var counts = new TreeMap<String, Integer>();
        var omitted = new ArrayList<String>();
        var output = transform(entries, counts, omitted);
        if (!counts.equals(new TreeMap<>(BlockStartupAdapter.EXPECTED_COUNTS)))
            throw new IllegalStateException("Incomplete V33 block batch: " + counts);
        if (omitted.size() != 4) throw new IllegalStateException("Unexpected generator omissions: " + omitted);
        var destination = root.resolve("reports/preview/bewitchment-v33-local-only.jar");
        try (var out = new ZipOutputStream(Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW))) {
            for (var entry : output.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("original_sha1", hash);
        report.put("block_startup_batch", counts);
        report.put("obsolete_generator_classes_omitted", omitted);
        report.put("added_resources", List.of(MEAT_TAG));
        report.put("installable_or_playable", false);
        Files.writeString(root.resolve("reports/translation-prototype-v33.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        System.out.println("V33 private block startup diagnostic: " + counts);
    }
}
