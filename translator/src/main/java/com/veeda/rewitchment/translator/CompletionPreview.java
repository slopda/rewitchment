package com.veeda.rewitchment.translator;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public final class CompletionPreview {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    static byte[] metadata(byte[] source) {
        var json = JsonParser.parseString(new String(source, StandardCharsets.UTF_8)).getAsJsonObject();

        json.remove("jars");
        var depends = json.getAsJsonObject("depends");
        depends.addProperty("minecraft", "~1.21.1");
        depends.addProperty("java", ">=21");
        for (var id : List.of("terraform-wood-api-v1", "impersonate", "playerabilitylib", "midnightlib", "fabric-permissions-api-v0", "rewitchment"))
            depends.addProperty(id, "*");
        return GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] mixins(byte[] source, Set<String> removed) {
        var json = JsonParser.parseString(new String(source, StandardCharsets.UTF_8)).getAsJsonObject();
        for (var section : List.of("mixins", "client", "server")) {
            if (!json.has(section)) continue;
            var kept = new JsonArray();
            for (var name : json.getAsJsonArray(section)) if (!removed.contains(name.getAsString())) kept.add(name);
            json.add(section, kept);
        }
        return GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] bookText(byte[] source) {
        var text = new String(source, StandardCharsets.UTF_8).replace("minecraft:scute", "minecraft:turtle_scute");
        return java.util.regex.Pattern.compile("\"(bewitchment:[a-z_]+)(\\{(?:[^\"\\\\]|\\\\.)*\\})\"").matcher(text).replaceAll(found ->
                java.util.regex.Matcher.quoteReplacement("\"" + found.group(1) + "[minecraft:custom_data=" + found.group(2) + "]\"")).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] accessWidener(byte[] source) {
        try (var input = CompletionPreview.class.getResourceAsStream("/v34-access-widener.txt")) {
            return (new String(source, StandardCharsets.UTF_8) + new String(input.readAllBytes(), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    static final Set<String> REMOVED_CLASSES = Set.of("moriyashiine/bewitchment/common/enchantment/MagicProtectionEnchantment",
            "moriyashiine/bewitchment/mixin/DamageEnchantmentMixin", "moriyashiine/bewitchment/mixin/vampirehack/ProtectionEnchantmentMixin",
            "moriyashiine/bewitchment/mixin/BlocksAccessor");

    static final Map<String, String> ADDED = Map.of(
            "data/bewitchment/enchantment/magic_protection.json", """
            {"description":{"translate":"enchantment.bewitchment.magic_protection"},"supported_items":"#minecraft:enchantable/armor",
            "weight":5,"max_level":4,"min_cost":{"base":10,"per_level_above_first":8},"max_cost":{"base":18,"per_level_above_first":8},
            "anvil_cost":2,"slots":["armor"],"exclusive_set":"#minecraft:exclusive_set/armor","effects":{"minecraft:damage_protection":[
            {"effect":{"type":"minecraft:add","value":{"type":"minecraft:linear","base":2,"per_level_above_first":2}},
            "requirements":{"condition":"minecraft:damage_source_properties","predicate":{"tags":[{"id":"minecraft:witch_resistant_to","expected":true}]}}}]}}
            """,
            "data/minecraft/tags/enchantment/non_treasure.json", "{\"replace\":false,\"values\":[\"bewitchment:magic_protection\"]}\n",
            "data/minecraft/tags/enchantment/exclusive_set/armor.json", "{\"replace\":false,\"values\":[\"bewitchment:magic_protection\"]}\n",
            "data/minecraft/tags/entity_type/sensitive_to_smite.json", "{\"replace\":false,\"values\":[\"bewitchment:ghost\",\"bewitchment:vampire\",\"bewitchment:baphomet\",\"bewitchment:demon\",\"bewitchment:hellhound\",\"bewitchment:herne\",\"bewitchment:leonard\",\"bewitchment:lilith\",\"bewitchment:werewolf\"]}\n",
            "data/minecraft/tags/entity_type/undead.json", "{\"replace\":false,\"values\":[\"bewitchment:ghost\",\"bewitchment:vampire\"]}\n");

    static final String OLD_BIOME_TAGS = "net/fabricmc/fabric/api/tag/convention/v1/ConventionalBiomeTags";
    static final String NEW_BIOME_TAGS = "net/fabricmc/fabric/api/tag/convention/v2/ConventionalBiomeTags";
    static final Map<String, String> BIOME_TAGS = Map.of(
            "TAIGA", "IS_TAIGA", "FOREST", "IS_FOREST", "SAVANNA", "IS_SAVANNA",
            "SWAMP", "IS_SWAMP", "PLAINS", "IS_PLAINS", "DESERT", "IS_DESERT",
            "JUNGLE", "IS_JUNGLE", "ICY", "IS_ICY", "MOUNTAIN", "IS_MOUNTAIN",
            "EXTREME_HILLS", "IS_HILL");

    static int migrateBiomeTags(ClassNode node) {
        int edits = 0;
        for (var method : node.methods) for (var instruction : method.instructions)
            if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field && field.owner.equals(OLD_BIOME_TAGS)) {
                var name = BIOME_TAGS.get(field.name);
                if (field.getOpcode() != org.objectweb.asm.Opcodes.GETSTATIC || name == null ||
                        !field.desc.equals("Lnet/minecraft/class_6862;"))
                    throw new IllegalStateException("Unknown legacy biome tag " + field.name + field.desc);
                field.owner = NEW_BIOME_TAGS;
                field.name = name;
                edits++;
            }
        return edits;
    }

    public static Map<String, byte[]> transform(Map<String, byte[]> entries, Map<String, Integer> counts) {
        var plain = new ArrayList<ClassNode>();
        for (var entry : entries.entrySet()) if (entry.getKey().endsWith(".class")) {
            var node = new ClassNode();
            new ClassReader(entry.getValue()).accept(node, ClassReader.SKIP_CODE);
            plain.add(node);
        }
        var scan = GameplayCompletionAdapter.scan(plain);
        var clientScan = ClientCompletionAdapter.scan(plain);
        var output = new LinkedHashMap<String, byte[]>();
        for (var entry : entries.entrySet()) {
            var name = entry.getKey();
            if (name.startsWith("META-INF/jars/")) continue;
            if (name.equals("fabric.mod.json")) output.put(name, metadata(entry.getValue()));
            else if (name.equals("bewitchment.mixins.json")) output.put(name, mixins(entry.getValue(), MixinMigrationAdapter.REMOVED));
            else if (name.equals("data/bewitchment/recipe/cauldron_brewing/hardening.json"))
                output.put(name, new String(entry.getValue(), StandardCharsets.UTF_8).replace("\"scute\"", "\"turtle_scute\"").getBytes(StandardCharsets.UTF_8));
            else if (name.endsWith(".json") && (name.contains("patchouli_books") || name.startsWith("data/")))
                output.put(name, bookText(entry.getValue()));
            else if (name.equals("bewitchment.accesswidener")) output.put(name, accessWidener(entry.getValue()));
            else if (name.endsWith(".class") && name.startsWith("moriyashiine/")) {
                var owner = name.substring(0, name.length() - 6);
                if (REMOVED_CLASSES.contains(owner)) continue;
                boolean migrated = MixinMigrationAdapter.MIGRATED.contains(owner);
                var node = new ClassNode();
                new ClassReader(entry.getValue()).accept(node, migrated ? ClassReader.EXPAND_FRAMES : 0);
                Map<String, Integer> edits = new TreeMap<>(GameplayCompletionAdapter.rewrite(node, scan));
                edits.putAll(RegressionRepairsAdapter.rewrite(node));
                int teleportFloor = TeleportFloorAdapter.rewrite(node);
                if (teleportFloor > 0) edits.put("teleport_world_floor", teleportFloor);
                int biomeTags = migrateBiomeTags(node);
                if (biomeTags > 0) edits.put("biome_tag_reads", biomeTags);
                edits.forEach((key, value) -> counts.merge(key, value, Integer::sum));
                var before = new String(entry.getValue(), StandardCharsets.ISO_8859_1);
                if (owner.contains("/mixin/")) {
                    int shadows = MixinMigrationAdapter.dropUnusedShadows(node);
                    if (shadows > 0) {
                        counts.merge("unused_shadows_removed", shadows, Integer::sum);
                        edits = Map.of("unused_shadows_removed", shadows);
                    }
                }
                var clientEdits = new TreeMap<String, Integer>();
                node = ClientCompletionAdapter.rewrite(node, clientScan, clientEdits);
                clientEdits.forEach((key, value) -> counts.merge(key, value, Integer::sum));
                if (edits.isEmpty()) edits = clientEdits;
                if (migrated || owner.contains("/mixin/") && before.contains("net/minecraft/class_1310")) {
                    node = MixinMigrationAdapter.rewrite(node);
                    counts.merge("mixin_classes_migrated", 1, Integer::sum);
                } else if (edits.isEmpty()) {
                    output.put(name, entry.getValue());
                    continue;
                }
                var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                output.put(name, writer.toByteArray());
            } else output.put(name, entry.getValue());
        }
        ADDED.forEach((path, json) -> {
            if (output.put(path, json.getBytes(StandardCharsets.UTF_8)) != null) throw new IllegalStateException("Shipped resource collision: " + path);
        });
        return output;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("No arguments supported");
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path original = root.resolve("../original/bewitchment-1.20-10.jar").normalize();
        var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(original)));
        if (!hash.equals(TranslationLab.RELEASE_SHA1)) throw new IllegalStateException("Unexpected original");
        var entries = new LinkedHashMap<String, byte[]>();
        try (var jar = new ZipFile(root.resolve("reports/preview/bewitchment-v33-local-only.jar").toFile())) {
            for (var entry : Collections.list(jar.entries())) if (!entry.isDirectory())
                try (var input = jar.getInputStream(entry)) {
                    entries.put(entry.getName(), input.readAllBytes());
                }
        }
        var counts = new TreeMap<String, Integer>();
        var output = transform(entries, counts);
        var destination = root.resolve("reports/preview/bewitchment-v34-local-only.jar");
        Files.deleteIfExists(destination);
        try (var out = new ZipOutputStream(Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW))) {
            for (var entry : output.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("original_sha1", hash);
        report.put("completion_batch", counts);
        report.put("installable_or_playable", false);
        Files.writeString(root.resolve("reports/translation-prototype-v34.json"), GSON.toJson(report) + "\n");
        System.out.println("V34 private boot candidate: " + counts);
    }
}
