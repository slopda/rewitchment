package com.veeda.rewitchment.translator;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public final class ArmorPreview {
    private ArmorPreview() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("No arguments supported");
        execute(false);
    }

    static void execute(boolean tools) throws Exception {
        execute(tools, false);
    }

    static void execute(boolean tools, boolean runtime) throws Exception {
        execute(tools, runtime, false);
    }

    static void execute(boolean tools, boolean runtime, boolean networking) throws Exception {
        execute(tools, runtime, networking, false);
    }

    static void execute(boolean tools, boolean runtime, boolean networking, boolean entities) throws Exception {
        execute(tools, runtime, networking, entities, false);
    }

    static void execute(boolean tools, boolean runtime, boolean networking, boolean entities, boolean crafting) throws Exception {
        execute(tools, runtime, networking, entities, crafting, false);
    }

    static void execute(boolean tools, boolean runtime, boolean networking, boolean entities, boolean crafting, boolean dataRecipes) throws Exception {
        if (dataRecipes && !crafting) throw new IllegalArgumentException("Data recipes require V28");
        if (crafting && !entities) throw new IllegalArgumentException("Crafting requires V27");
        if (entities && !networking) throw new IllegalArgumentException("Entities require V26");
        if (networking && (!tools || !runtime)) throw new IllegalArgumentException("Networking requires V25");
        String version = dataRecipes ? "v29" : crafting ? "v28" : entities ? "v27" : networking ? "v26" : runtime ? "v25" : tools ? "v24" : "v23";
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path source = root.resolve("../original/bewitchment-1.20-10.jar").normalize();
        Path destination = root.resolve("reports/preview/bewitchment-" + version + "-local-only.jar");
        Path report = root.resolve("reports/translation-prototype-" + version + ".json");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                .digest(Files.readAllBytes(source)));
        if (!TranslationLab.RELEASE_SHA1.equals(hash)) throw new IllegalStateException("Unexpected original SHA1");
        if (Files.exists(destination)) throw new IllegalStateException("Refusing to overwrite previous preview");
        Map<String, byte[]> output = new LinkedHashMap<>();
        int materials = 0, items = 0, rejected = 0, originalClasses = 0, changed = 0;
        int silver = 0, swords = 0, mining = 0, reach = 0, speed = 0, durability = 0;
        Map<String, Integer> runtimeCounts = new java.util.TreeMap<>();
        Map<String, Integer> networkCounts = new java.util.TreeMap<>();
        Map<String, Integer> entityCounts = new java.util.TreeMap<>();
        Map<String, Integer> craftingCounts = new java.util.TreeMap<>();
        Map<String, Integer> dataCounts = new java.util.TreeMap<>();
        var omitted = new ArrayList<String>();
        var remapper = new LibraryNameRemapper();
        try (ZipFile jar = new ZipFile(source.toFile())) {
            for (ZipEntry entry : java.util.Collections.list(jar.entries())) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                byte[] before;
                try (var input = jar.getInputStream(entry)) {
                    before = input.readAllBytes();
                }
                byte[] content = before;
                if (name.endsWith(".class")) {
                    originalClasses++;
                    String owner = name.substring(0, name.length() - 6);
                    if (ArmorMaterialAdapter.OBSOLETE.contains(owner)
                            || tools && ToolAndWeaponAdapter.SILVER_CLASS.equals(owner)) {
                        omitted.add(owner);
                        continue;
                    }
                    content = TranslationLab.rewrite(content, remapper);
                    ClassNode node = new ClassNode();
                    new ClassReader(content).accept(node,
                            entities && (EntityLifecycleAdapter.TRACKERS.contains(owner) || EntityLifecycleAdapter.SPAWNERS.contains(owner)) || runtime && ItemRuntimeAdapter.expandedFrames(owner) ? ClassReader.EXPAND_FRAMES : 0);
                    var result = ArmorMaterialAdapter.rewrite(node);
                    materials += result.materials();
                    items += result.armorItems();
                    rejected += result.rejected();
                    if (tools) {
                        var weapons = ToolAndWeaponAdapter.rewrite(node);
                        silver += weapons.silverFactories();
                        swords += weapons.swordConstructors();
                        mining += weapons.miningConstructors();
                        reach += weapons.reachOverrides();
                        speed += weapons.miningSpeedFields();
                        durability += weapons.explicitDurabilities();
                        rejected += weapons.rejected();
                    }
                    if (runtime) ItemRuntimeAdapter.rewrite(node).counts.forEach(
                            (key, value) -> runtimeCounts.merge(key, value, Integer::sum));
                    if (networking) {
                        var packets = PacketTransportAdapter.rewrite(node);
                        node = packets.node();
                        packets.counts().forEach((key, value) -> networkCounts.merge(key, value, Integer::sum));
                    }
                    if (entities) EntityLifecycleAdapter.rewrite(node).forEach(
                            (key, value) -> entityCounts.merge(key, value, Integer::sum));
                    if (crafting) {
                        var recipes = CraftingRecipeAdapter.rewrite(node);
                        node = recipes.node();
                        recipes.counts().forEach((key, value) -> craftingCounts.merge(key, value, Integer::sum));
                    }
                    boolean dataChanged = false;
                    if (dataRecipes) {
                        var recipes = DataRecipeAdapter.rewrite(node);
                        node = recipes.node();
                        recipes.counts().forEach((key, value) -> dataCounts.merge(key, value, Integer::sum));
                        dataChanged = !recipes.counts().isEmpty();
                    }
                    ClassWriter writer = new ClassWriter(dataChanged ? ClassWriter.COMPUTE_MAXS : 0);
                    node.accept(writer);
                    content = writer.toByteArray();
                    if (!java.util.Arrays.equals(before, content)) changed++;
                } else if (name.equals("bewitchment-refmap.json")) {
                    content = MixinHandlerAdapter.rewriteRefmap(content, true).bytes();
                }
                output.put(name, content);
            }
        }
        if (materials != 4 || items != 13 || rejected != 0 || omitted.size() != (tools ? 5 : 4)
                || originalClasses != 472) {
            throw new IllegalStateException("Incomplete armor migration: " + materials + "/" + items + "/" + rejected);
        }
        if (tools && (silver != 1 || swords != 2 || mining != 1 || reach != 2 || speed != 1 || durability != 2)) {
            throw new IllegalStateException("Incomplete V24 tool/weapon migration: "
                    + silver + "/" + swords + "/" + mining + "/" + reach + "/" + speed);
        }
        if (runtime && !runtimeCounts.equals(ItemRuntimeAdapter.EXPECTED_COUNTS))
            throw new IllegalStateException("Incomplete V25 runtime batch: " + runtimeCounts);
        if (networking && !networkCounts.equals(PacketTransportAdapter.EXPECTED_COUNTS))
            throw new IllegalStateException("Incomplete V26 networking batch: " + networkCounts);
        if (entities && !entityCounts.equals(EntityLifecycleAdapter.EXPECTED_COUNTS))
            throw new IllegalStateException("Incomplete V27 entity batch: " + entityCounts);
        if (crafting && !craftingCounts.equals(CraftingRecipeAdapter.EXPECTED_COUNTS))
            throw new IllegalStateException("Incomplete V28 crafting batch: " + craftingCounts);
        if (dataRecipes && !dataCounts.equals(DataRecipeAdapter.EXPECTED_COUNTS))
            throw new IllegalStateException("Incomplete V29 data recipe batch: " + dataCounts);
        Files.createDirectories(destination.getParent());
        try (var stream = new ZipOutputStream(Files.newOutputStream(destination,
                java.nio.file.StandardOpenOption.CREATE_NEW))) {
            for (var entry : output.entrySet()) {
                stream.putNextEntry(new ZipEntry(entry.getKey()));
                stream.write(entry.getValue());
                stream.closeEntry();
            }
        }
        if (!hash.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                .digest(Files.readAllBytes(source))))) throw new IllegalStateException("Original changed");
        var result = new LinkedHashMap<String, Object>(Map.of("original_sha1", hash, "original_class_count", originalClasses,
                "output_class_count", originalClasses - omitted.size(),
                "retained_classes_translated", changed, "armor_material_factories", materials,
                "armor_item_factories", items, "rejected_constructors", rejected,
                "obsolete_anonymous_classes_omitted", omitted,
                "installable_or_playable", false));
        if (tools) {
            result.put("silver_material_factories", silver);
            result.put("sword_super_constructors", swords);
            result.put("mining_super_constructors", mining);
            result.put("reach_overrides_replaced_by_components", reach);
            result.put("caduceus_mining_speed_field_reads", speed);
            result.put("explicit_weapon_durability_materials", durability);
        }
        if (dataRecipes) result.put("data_recipe_batch", dataCounts);
        if (crafting) result.put("crafting_batch", craftingCounts);
        if (runtime) result.put("runtime_batch", runtimeCounts);
        if (entities) result.put("entity_lifecycle_batch", entityCounts);
        if (networking) result.put("networking_batch", networkCounts);
        Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
        System.out.println(version + " private diagnostic: " + materials + " materials, " + items
                + " armor constructors; " + swords + " sword + " + mining + " mining constructors");
        if (runtime) System.out.println("V25 runtime adaptations: " + runtimeCounts);
        if (networking) System.out.println("V26 networking adaptations: " + networkCounts);
    }
}
