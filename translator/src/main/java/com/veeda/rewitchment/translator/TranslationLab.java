package com.veeda.rewitchment.translator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;

public final class TranslationLab {
    public static final String RELEASE_SHA1 = "11ac0992d1e54af8515b6c339d0f6bfc18b4d2d0";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> TRACKED_PREFIXES = LibraryNameRemapper.PACKAGE_MAPPINGS.keySet();

    public record Summary(String inputSha1, boolean officialRelease, String modId, String minecraftConstraint,
                          int classFiles, int classesChanged, int embeddedJars,
                          int identifierFactoriesConverted, int identifierFactoriesRejected,
                          int effectConstructorsConverted, int effectConstructorsRejected,
                          int effectTypeGettersConverted,
                          int customNbtWritesConverted, int specialNbtKeysSkipped,
                          int unsafeNbtWritesSkipped, int customNbtReadsConverted,
                          int unbreakableComponentWritesConverted,
                          int registryFieldsUnwrapped,
                          int mixinHandlersAdapted, int mixinRefmapEntriesAdapted,
                          int statusEffectCallsAdapted, int attributeBuilderCallsAdapted,
                          int modelPartRendersAdapted, int modelRendersAdapted,
                          int fireDurationCallsAdapted,
                          int itemCustomNameCallsAdapted,
                          int hasLegacyNbtChecksAdapted,
                          int armorMaterialGettersAdapted,
                          int blockEntityNbtOverridesMigrated,
                          int blockEntityNbtCallsMigrated,
                          int blockEntityInventoryNbtCallsMigrated,
                          int persistentStateNbtReadersAdapted,
                          int persistentStateNbtWritersAdapted,
                          int persistentStateTypeRegistrationsAdapted,
                          int persistentStateInventoryReadsAdapted,
                          int persistentStateInventoryWritesAdapted,
                          int persistentStateLambdaCapturesAdapted,
                          int taglockNbtMethodsAdapted,
                          int taglockInventoryCallsAdapted,
                          int taglockBlockEntityCallsAdapted,
                          int packetInventoryCallsAdapted,
                          int entityItemStacksReadAdapted,
                          int entityItemStacksWrittenAdapted,
                          int silkTouchGuardsAdapted,
                          int legacyEnchantmentMapReadsAdapted,
                          int legacyEnchantmentLevelReadsAdapted,
                          int legacyEnchantmentMapClearsAdapted,
                          int legacyEnchantmentRepairCostWritesAdapted,
                          int smiteEquipmentLevelQueriesAdapted,
                          int legacyAttributeModifierConstructorsAdapted,
                          int inheritedEntityRandomCallsAdapted,
                          int arthropodCreatureGroupChecksAdapted,
                          int illagerCreatureGroupChecksAdapted,
                          int nativeAttributeModifierIdentityChecksAdapted,
                          int potionUtilityCallsAdapted, int potionColorComponentWritesAdapted,
                          Map<String, Integer> mappedSymbolsByPrefix,
                          Map<String, Integer> remainingLegacySymbolsByPrefix,
                          List<String> blockers, String previewPath) {}

    private TranslationLab() {}

    private static String sha1(Path input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (var stream = Files.newInputStream(input)) {
                byte[] chunk = new byte[64 * 1024];
                for (int count; (count = stream.read(chunk)) != -1;) {
                    digest.update(chunk, 0, count);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static JsonObject metadata(ZipFile jar) throws IOException {
        ZipEntry entry = jar.getEntry("fabric.mod.json");
        if (entry == null) {
            throw new IOException("Not a Fabric mod JAR: fabric.mod.json is missing");
        }
        try (var reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    public static Map<String, Integer> libraryReferences(byte[] bytes) {
        var counts = new TreeMap<String, Integer>();
        ClassReader reader = new ClassReader(bytes);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            private void count(String name) {
                if (name == null) return;
                for (String prefix : TRACKED_PREFIXES) {
                    if (name.startsWith(prefix)) {
                        counts.merge(prefix, 1, Integer::sum);
                    }
                }
            }

            private void descriptor(String descriptor) {
                if (descriptor == null) return;
                try {
                    Type type = Type.getType(descriptor);
                    countType(type);
                } catch (IllegalArgumentException ignored) {

                    try {
                        for (Type type : Type.getArgumentTypes(descriptor)) countType(type);
                        countType(Type.getReturnType(descriptor));
                    } catch (IllegalArgumentException ignoredAgain) {

                    }
                }
            }

            private void countType(Type type) {
                if (type.getSort() == Type.ARRAY) countType(type.getElementType());
                else if (type.getSort() == Type.OBJECT) count(type.getInternalName());
                else if (type.getSort() == Type.METHOD) {
                    for (var argument : type.getArgumentTypes()) countType(argument);
                    countType(type.getReturnType());
                }
            }

            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                count(superName);
                if (interfaces != null) for (String it : interfaces) count(it);
            }

            @Override
            public org.objectweb.asm.FieldVisitor visitField(int access, String name,
                                                               String desc, String sig, Object value) {
                descriptor(desc);
                return null;
            }

            @Override
            public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String desc,
                                                                 String sig, String[] exceptions) {
                descriptor(desc);
                return new org.objectweb.asm.MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitTypeInsn(int opcode, String type) { count(type); }
                    @Override public void visitFieldInsn(int opcode, String owner, String field, String d) {
                        count(owner); descriptor(d);
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String d, boolean itf) {
                        count(owner); descriptor(d);
                    }
                    @Override public void visitLdcInsn(Object constant) {
                        if (constant instanceof Type t) countType(t);
                    }
                };
            }
        }, ClassReader.SKIP_FRAMES);
        return counts;
    }

    public static final class RewriteStats {
        public int identifierFactoriesConverted;
        public int identifierFactoriesRejected;
        public int effectConstructorsConverted;
        public int effectConstructorsRejected;
        public int effectTypeGettersConverted;
        public int customNbtWritesConverted;
        public int specialNbtKeysSkipped;
        public int unsafeNbtWritesSkipped;
        public int customNbtReadsConverted;
        public int unbreakableComponentWritesConverted;
        public int registryFieldsUnwrapped;
        public int mixinHandlersAdapted;
        public int mixinRefmapEntriesAdapted;
        public int statusEffectCallsAdapted;
        public int attributeBuilderCallsAdapted;
        public int modelPartRendersAdapted;
        public int modelRendersAdapted;
        public int fireDurationCallsAdapted;
        public int itemCustomNameCallsAdapted;
        public int hasLegacyNbtChecksAdapted;
        public int armorMaterialGettersAdapted;
        public int blockEntityNbtOverridesMigrated;
        public int blockEntityNbtCallsMigrated;
        public int blockEntityInventoryNbtCallsMigrated;
        public int persistentStateNbtReadersAdapted;
        public int persistentStateNbtWritersAdapted;
        public int persistentStateTypeRegistrationsAdapted;
        public int persistentStateInventoryReadsAdapted;
        public int persistentStateInventoryWritesAdapted;
        public int persistentStateLambdaCapturesAdapted;
        public int taglockNbtMethodsAdapted;
        public int taglockInventoryCallsAdapted;
        public int taglockBlockEntityCallsAdapted;
        public int packetInventoryCallsAdapted;
        public int entityItemStacksReadAdapted;
        public int entityItemStacksWrittenAdapted;
        public int silkTouchGuardsAdapted;
        public int legacyEnchantmentMapReadsAdapted;
        public int legacyEnchantmentLevelReadsAdapted;
        public int legacyEnchantmentMapClearsAdapted;
        public int legacyEnchantmentRepairCostWritesAdapted;
        public int smiteEquipmentLevelQueriesAdapted;
        public int legacyAttributeModifierConstructorsAdapted;
        public int inheritedEntityRandomCallsAdapted;
        public int arthropodCreatureGroupChecksAdapted;
        public int illagerCreatureGroupChecksAdapted;
        public int nativeAttributeModifierIdentityChecksAdapted;
        public int potionUtilityCallsAdapted;
        public int potionColorComponentWritesAdapted;
    }

    public static byte[] rewrite(byte[] classBytes, LibraryNameRemapper remapper) {
        return rewrite(classBytes, remapper, new RewriteStats());
    }

    public static byte[] rewrite(byte[] classBytes, LibraryNameRemapper remapper,
                                 RewriteStats stats) {

        ClassReader original = new ClassReader(classBytes);
        ClassNode intermediate = new ClassNode();

        original.accept(new ClassRemapper(intermediate, remapper),
                (BlockEntitySerializationAdapter.isTargetClass(original.getClassName())
                        || RemainingInventoryNbtAdapter.usesExpandedFrames(original.getClassName())
                        || PersistentStateNbtAdapter.usesExpandedFrames(original.getClassName()))
                        ? ClassReader.EXPAND_FRAMES : 0);
        IdentifierFactoryAdapter.Outcome identifier = IdentifierFactoryAdapter.rewrite(intermediate);
        stats.identifierFactoriesConverted += identifier.converted();
        stats.identifierFactoriesRejected += identifier.rejected();
        LegacyEffectAdapter.Outcome effect = LegacyEffectAdapter.rewrite(intermediate);
        stats.effectConstructorsConverted += effect.constructorsConverted();
        stats.effectConstructorsRejected += effect.constructorsRejected();
        stats.effectTypeGettersConverted += effect.gettersConverted();
        LegacyItemNbtAdapter.Outcome itemNbt = LegacyItemNbtAdapter.rewrite(intermediate);
        stats.customNbtWritesConverted += itemNbt.converted();
        stats.specialNbtKeysSkipped += itemNbt.protectedKeysSkipped();
        stats.unsafeNbtWritesSkipped += itemNbt.unsafeSkipped();
        stats.customNbtReadsConverted += itemNbt.readsConverted();
        stats.unbreakableComponentWritesConverted += itemNbt.unbreakableConverted();
        stats.potionColorComponentWritesAdapted += itemNbt.potionColorConverted();
        stats.registryFieldsUnwrapped += RegistryFieldAdapter.rewrite(intermediate);
        LegacyMethodAdapter.Outcome methodChanges = LegacyMethodAdapter.rewrite(intermediate);
        stats.statusEffectCallsAdapted += methodChanges.statusEffects();
        stats.attributeBuilderCallsAdapted += methodChanges.attributes();
        stats.modelPartRendersAdapted += methodChanges.modelParts();
        stats.modelRendersAdapted += methodChanges.models();
        stats.fireDurationCallsAdapted += methodChanges.fireConversions();
        stats.itemCustomNameCallsAdapted += methodChanges.customNames();
        stats.hasLegacyNbtChecksAdapted += methodChanges.hasLegacyNbtChecks();
        stats.armorMaterialGettersAdapted += methodChanges.armorMaterials();
        BlockEntitySerializationAdapter.Outcome nbt = BlockEntitySerializationAdapter.rewrite(intermediate);
        stats.blockEntityNbtOverridesMigrated += nbt.overridesMigrated();
        stats.blockEntityNbtCallsMigrated += nbt.vanillaCallsMigrated();
        stats.blockEntityInventoryNbtCallsMigrated += nbt.inventoriesMigrated();
        RemainingInventoryNbtAdapter.Outcome remaining =
                RemainingInventoryNbtAdapter.rewrite(intermediate);
        stats.taglockNbtMethodsAdapted += remaining.taglockMethodDeclarations();
        stats.taglockInventoryCallsAdapted += remaining.taglockInventoryCalls();
        stats.taglockBlockEntityCallsAdapted += remaining.taglockBlockEntityCalls();
        stats.packetInventoryCallsAdapted += remaining.packetInventoryCalls();
        PersistentStateNbtAdapter.Outcome state = PersistentStateNbtAdapter.rewrite(intermediate);
        stats.persistentStateNbtReadersAdapted += state.readerMethods();
        stats.persistentStateNbtWritersAdapted += state.writerOverrides();
        stats.persistentStateTypeRegistrationsAdapted += state.persistenceTypeRegistrations();
        stats.persistentStateInventoryReadsAdapted += state.readInventoryCalls();
        stats.persistentStateInventoryWritesAdapted += state.capturedInventoryWriterCalls();
        stats.persistentStateLambdaCapturesAdapted += state.registryAwareLambdas();
        LegacyEntityStackAdapter.Outcome entityStacks = LegacyEntityStackAdapter.rewrite(intermediate);
        stats.entityItemStacksReadAdapted += entityStacks.entityStackReads();
        stats.entityItemStacksWrittenAdapted += entityStacks.entityStackWrites();
        LegacyEnchantmentAdapter.Outcome enchantments =
                LegacyEnchantmentAdapter.rewrite(intermediate);
        stats.silkTouchGuardsAdapted += enchantments.silkTouchGuards();
        stats.legacyEnchantmentMapReadsAdapted += enchantments.legacyMapReads();
        stats.legacyEnchantmentLevelReadsAdapted += enchantments.legacyLevelReads();
        stats.legacyEnchantmentMapClearsAdapted += enchantments.legacyMapClears();
        stats.legacyEnchantmentRepairCostWritesAdapted += enchantments.repairCostWrites();
        stats.smiteEquipmentLevelQueriesAdapted += enchantments.equipmentLevelQueries();
        LegacyAttributeAndRandomAdapter.Outcome gameplay =
                LegacyAttributeAndRandomAdapter.rewrite(intermediate);
        stats.legacyAttributeModifierConstructorsAdapted += gameplay.uuidModifierConstructors();
        stats.inheritedEntityRandomCallsAdapted += gameplay.inheritedRandomCalls();
        CreatureTagsAndAttributesAdapter.Outcome tagChecks =
                CreatureTagsAndAttributesAdapter.rewrite(intermediate);
        stats.arthropodCreatureGroupChecksAdapted += tagChecks.arthropodComparisons();
        stats.illagerCreatureGroupChecksAdapted += tagChecks.illagerComparisons();
        stats.nativeAttributeModifierIdentityChecksAdapted += tagChecks.nativeAttributeIdChecks();
        stats.potionUtilityCallsAdapted += LegacyPotionAdapter.rewrite(intermediate);
        stats.mixinHandlersAdapted += MixinHandlerAdapter.rewrite(intermediate);
        ClassWriter output = new ClassWriter(0);
        intermediate.accept(output);
        return output.toByteArray();
    }

    public static Summary execute(Path input, Path reportPath, Path previewPath) throws IOException {
        Path source = input.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) throw new IOException("Missing source JAR: " + source);
        if (previewPath != null) {
            Path dest = previewPath.toAbsolutePath().normalize();
            if (dest.equals(source)) throw new IOException("Refusing to overwrite the original JAR");

            if (!dest.startsWith(reportPath.toAbsolutePath().normalize().getParent())) {
                throw new IOException("Local preview must stay beneath the report directory");
            }
            if (Files.exists(dest)) throw new IOException("Refusing to overwrite previous local preview: " + dest);
        }
        var originalHash = sha1(source);
        var before = new TreeMap<String, Integer>();
        var after = new TreeMap<String, Integer>();
        int classes = 0, changed = 0, embedded = 0;
        RewriteStats stats = new RewriteStats();
        String modId, minecraft;
        var remapper = new LibraryNameRemapper();
        try (ZipFile jar = new ZipFile(source.toFile())) {
            JsonObject data = metadata(jar);
            modId = data.get("id").getAsString();
            minecraft = data.getAsJsonObject("depends").get("minecraft").getAsString();
            if (!modId.equals("bewitchment") || !minecraft.contains("1.20.1")) {
                throw new IOException("Only the original Bewitchment Fabric 1.20.1 JAR is supported for this experiment");
            }
            if (previewPath != null) Files.createDirectories(previewPath.toAbsolutePath().getParent());
            try (ZipOutputStream preview = previewPath == null ? null
                    : new ZipOutputStream(Files.newOutputStream(previewPath))) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    byte[] content;
                    try (var stream = jar.getInputStream(entry)) { content = stream.readAllBytes(); }
                    String name = entry.getName();
                    if (name.endsWith(".jar")) embedded++;
                    if (name.endsWith(".class")) {
                        classes++;
                        var oldRefs = libraryReferences(content);
                        oldRefs.forEach((prefix, count) -> before.merge(prefix, count, Integer::sum));
                        byte[] mapped = rewrite(content, remapper, stats);
                        var remaining = libraryReferences(mapped);
                        remaining.forEach((prefix, count) -> after.merge(prefix, count, Integer::sum));
                        if (!java.util.Arrays.equals(content, mapped)) changed++;
                        content = mapped;
                    }
                    if ("bewitchment-refmap.json".equals(name)) {

                        var refmap = MixinHandlerAdapter.rewriteRefmap(content, true);
                        stats.mixinRefmapEntriesAdapted += refmap.changes();
                        content = refmap.bytes();
                    }
                    if (preview != null) {

                        preview.putNextEntry(new ZipEntry(name));
                        preview.write(content);
                        preview.closeEntry();
                    }
                }
            }
            if (stats.mixinHandlersAdapted != stats.mixinRefmapEntriesAdapted) {
                throw new IOException("Refusing an unmatched Mixin handler/refmap adaptation");
            }
        }
        List<String> blockers = new ArrayList<>(List.of(
                "Only selected library names, Identifier, status effects, direct constant-key custom NBT reads/writes and Unbreakable set are adapted; arbitrary mutable ItemStack.getNbt() and getOrCreateNbt() behavior remains incompatible",
                "Vanilla item NBT keys moved to dedicated data components; protected keys are intentionally not converted to CUSTOM_DATA",
                "Selected registry static fields are unwrapped to their original raw types, but APIs expecting new RegistryEntry values still need separate adapters",
                "Limited LivingEntity status-effect, attribute builder, client model ARGB render and fire-duration compatibility methods are rewritten; unrelated API/mixin changes remain",
                "Limited potion itemstack operations use native POTION_CONTENTS; legacy potion NBT serialization, missing-EMPTY semantics and tooltip migration require separate review",
                "A single brew PotionEntityMixin injection handler/refmap selector is adapted for Iterable; other injections and Mixin semantics remain unported",
                "Bewitchment's 80 Mixins and its access widener are not translated",
                "Original fabric.mod.json and 7 nested JAR dependencies are unchanged",
                "Modern CCA's API methods and lifecycle may not be binary/behaviorally compatible",
                "Modern Impersonate package relocation conflicts with earlier facade approach; select exactly one",
                "No integration yet with Legacy Connector's FML locator, Fabric Loader or Sinytra Connector"
        ));
        Summary summary = new Summary(originalHash, RELEASE_SHA1.equals(originalHash),
                modId, minecraft, classes, changed, embedded,
                stats.identifierFactoriesConverted, stats.identifierFactoriesRejected,
                stats.effectConstructorsConverted, stats.effectConstructorsRejected,
                stats.effectTypeGettersConverted,
                stats.customNbtWritesConverted, stats.specialNbtKeysSkipped,
                stats.unsafeNbtWritesSkipped, stats.customNbtReadsConverted,
                stats.unbreakableComponentWritesConverted,
                stats.registryFieldsUnwrapped,
                stats.mixinHandlersAdapted, stats.mixinRefmapEntriesAdapted,
                stats.statusEffectCallsAdapted, stats.attributeBuilderCallsAdapted,
                stats.modelPartRendersAdapted, stats.modelRendersAdapted,
                stats.fireDurationCallsAdapted,
                stats.itemCustomNameCallsAdapted,
                stats.hasLegacyNbtChecksAdapted,
                stats.armorMaterialGettersAdapted,
                stats.blockEntityNbtOverridesMigrated,
                stats.blockEntityNbtCallsMigrated,
                stats.blockEntityInventoryNbtCallsMigrated,
                stats.persistentStateNbtReadersAdapted,
                stats.persistentStateNbtWritersAdapted,
                stats.persistentStateTypeRegistrationsAdapted,
                stats.persistentStateInventoryReadsAdapted,
                stats.persistentStateInventoryWritesAdapted,
                stats.persistentStateLambdaCapturesAdapted,
                stats.taglockNbtMethodsAdapted,
                stats.taglockInventoryCallsAdapted,
                stats.taglockBlockEntityCallsAdapted,
                stats.packetInventoryCallsAdapted,
                stats.entityItemStacksReadAdapted,
                stats.entityItemStacksWrittenAdapted,
                stats.silkTouchGuardsAdapted,
                stats.legacyEnchantmentMapReadsAdapted,
                stats.legacyEnchantmentLevelReadsAdapted,
                stats.legacyEnchantmentMapClearsAdapted,
                stats.legacyEnchantmentRepairCostWritesAdapted,
                stats.smiteEquipmentLevelQueriesAdapted,
                stats.legacyAttributeModifierConstructorsAdapted,
                stats.inheritedEntityRandomCallsAdapted,
                stats.arthropodCreatureGroupChecksAdapted,
                stats.illagerCreatureGroupChecksAdapted,
                stats.nativeAttributeModifierIdentityChecksAdapted,
                stats.potionUtilityCallsAdapted, stats.potionColorComponentWritesAdapted,
                new LinkedHashMap<>(before), new LinkedHashMap<>(after),
                blockers, previewPath == null ? null : previewPath.toAbsolutePath().toString());
        Files.createDirectories(reportPath.toAbsolutePath().getParent());
        Files.writeString(reportPath, GSON.toJson(summary) + "\n", StandardCharsets.UTF_8);
        if (!sha1(source).equals(originalHash)) throw new IOException("Original JAR unexpectedly changed!");
        return summary;
    }
}
