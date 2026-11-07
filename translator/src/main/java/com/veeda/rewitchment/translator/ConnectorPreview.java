package com.veeda.rewitchment.translator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class ConnectorPreview {
    static final String POPPET_LIVING = "moriyashiine/bewitchment/mixin/poppet/LivingEntityMixin.class";

    static final String VOODOO_DROWN = "bewitchment$voodooDrownEffect";
    static final Set<String> ATLAS_READERS = Set.of("moriyashiine/bewitchment/client/misc/SpriteIdentifiers.class", "moriyashiine/bewitchment/client/BewitchmentClient.class");
    static final String SHEETS = "net/minecraft/class_4722";
    static final Map<String, String> ATLASES = Map.of("field_21709", "textures/atlas/chest.png", "field_21708", "textures/atlas/signs.png");
    static final String IDENTIFIER = "net/minecraft/class_2960";
    static final String OF_VANILLA = "method_60656";
    static final Pattern INTERMEDIARY =Pattern.compile("net[/.]minecraft[/.]class_\\d+(?:\\$class_\\d+)*|\\b(?:method|field|comp)_\\d+");

    static void removeVoodooDrown(ClassNode node) {
        if (!node.methods.removeIf(method -> method.name.equals(VOODOO_DROWN)))
            throw new IllegalStateException("Missing " + VOODOO_DROWN);
    }

    static String mojang(String value, Map<String, String> names, int[] rewritten) {
        var found = INTERMEDIARY.matcher(value);
        if (!found.find()) return value;
        rewritten[0]++;
        return found.replaceAll(match -> {
            var token = match.group();
            boolean dotted = token.startsWith("net.");
            var mapped = names.get(token.replace('.', '/'));
            if (mapped == null) throw new IllegalStateException("No Mojang name for " + token + " in " + value);
            return Matcher.quoteReplacement(dotted ? mapped.replace('/', '.') : mapped);
        });
    }

    @SuppressWarnings("unchecked")
    static Object rewriteValue(Object value, Map<String, String> names, Set<String> refmapped, int[] rewritten) {
        if (value instanceof String text) return refmapped.contains(text) ? text : mojang(text, names, rewritten);
        if (value instanceof AnnotationNode nested) rewriteAnnotation(nested, names, refmapped, rewritten);
        if (value instanceof List<?> list) ((List<Object>) list).replaceAll(item -> rewriteValue(item, names, refmapped, rewritten));
        return value;
    }

    static void rewriteAnnotation(AnnotationNode annotation, Map<String, String> names, Set<String> refmapped, int[] rewritten) {
        if (annotation.values == null) return;
        for (int i = 1; i < annotation.values.size(); i += 2)
            annotation.values.set(i, rewriteValue(annotation.values.get(i), names, refmapped, rewritten));
    }

    static void rewriteAll(List<AnnotationNode> annotations, Map<String, String> names, Set<String> refmapped, int[] rewritten) {
        if (annotations == null) return;
        for (var annotation : annotations)
            if (annotation.desc.startsWith("Lorg/spongepowered/") || annotation.desc.startsWith("Lcom/llamalad7/"))
                rewriteAnnotation(annotation, names, refmapped, rewritten);
    }

    static boolean isInjector(MethodNode method) {
        return method.instructions.size() > 0 && method.visibleAnnotations != null
                && method.visibleAnnotations.stream().anyMatch(annotation -> annotation.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/"));
    }

    static void restoreParameterTable(ClassNode owner, MethodNode method) {
        if (method.localVariables != null && !method.localVariables.isEmpty() || !isInjector(method)) return;
        var start = new LabelNode();
        var end = new LabelNode();
        method.instructions.insert(start);
        method.instructions.add(end);
        method.localVariables = new ArrayList<>();
        int slot = 0;
        if ((method.access & Opcodes.ACC_STATIC) == 0) {
            method.localVariables.add(new LocalVariableNode("this", "L" + owner.name + ";", null, start, end, slot));
            slot = 1;
        }
        int index = 0;
        for (var type : Type.getArgumentTypes(method.desc)) {
            method.localVariables.add(new LocalVariableNode("arg" + index++, type.getDescriptor(), null, start, end, slot));
            slot += type.getSize();
        }
    }

    static byte[] inlineAtlases(byte[] source) {
        var node = new ClassNode();
        new ClassReader(source).accept(node, 0);
        int inlined = 0;
        for (var method : node.methods)
            for (var instruction : method.instructions.toArray())
                if (instruction instanceof FieldInsnNode field && field.owner.equals(SHEETS) && field.getOpcode() == Opcodes.GETSTATIC) {
                    var path = ATLASES.get(field.name);
                    if (path == null) throw new IllegalStateException("Unexpected Sheets read " + field.name + " in " + node.name);
                    method.instructions.insertBefore(field, new LdcInsnNode(path));
                    method.instructions.set(field, new MethodInsnNode(Opcodes.INVOKESTATIC, IDENTIFIER, OF_VANILLA, "(Ljava/lang/String;)L" + IDENTIFIER + ";", false));
                    inlined++;
                }
        if (inlined == 0) throw new IllegalStateException("No atlas reads in " + node.name);
        var writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    static byte[] mixinClass(String path, byte[] source, Map<String, String> names, JsonObject refmap, int[] rewritten) {
        var node = new ClassNode();
        new ClassReader(source).accept(node, 0);
        if (path.equals(POPPET_LIVING)) removeVoodooDrown(node);
        var own = refmap.getAsJsonObject(node.name);
        Set<String> refmapped = own == null ? Set.of() : own.keySet();
        rewriteAll(node.visibleAnnotations, names, refmapped, rewritten);
        rewriteAll(node.invisibleAnnotations, names, refmapped, rewritten);
        for (var method : node.methods) {
            restoreParameterTable(node, method);
            rewriteAll(method.visibleAnnotations, names, refmapped, rewritten);
            rewriteAll(method.invisibleAnnotations, names, refmapped, rewritten);
        }
        for (var field : node.fields) {
            rewriteAll(field.visibleAnnotations, names, refmapped, rewritten);
            rewriteAll(field.invisibleAnnotations, names, refmapped, rewritten);
        }
        var writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    static Map<String, String> names(Path tsv) throws java.io.IOException {
        var names = new HashMap<String, String>();
        for (var line : Files.readAllLines(tsv)) {
            var split = line.split("\t");
            names.put(split[0], split[1]);
        }
        return names;
    }

    static byte[] optionalStatsRedirect(byte[] source) {
        var node = new ClassNode(); new ClassReader(source).accept(node, 0);
        int patched = 0;
        for (var method : node.methods) if (method.name.equals("resolvePlayerStatsName")) {
            if (method.visibleAnnotations == null) throw new IllegalStateException("Stats Redirect annotations missing");
            for (var annotation : method.visibleAnnotations) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")) {
                for (int i = 0; i < annotation.values.size(); i += 2)
                    if (annotation.values.get(i).equals("require")) throw new IllegalStateException("Unexpected prepatched stats Redirect");
                annotation.values.add("require"); annotation.values.add(0); patched++;
            }
        }
        if (patched != 1) throw new IllegalStateException("Expected one stats-name Redirect, found " + patched);
        var writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray();
    }

    static void prepareImpersonate(Path root) throws java.io.IOException {
        var file = "impersonate-3.2.1-standalone.jar";
        try (var jar = new ZipFile(root.resolve("test-libs/" + file).toFile());
             var out = new ZipOutputStream(Files.newOutputStream(root.resolve("reports/preview/" + file)))) {
            for (var entry : Collections.list(jar.entries())) {
                out.putNextEntry(new ZipEntry(entry.getName()));
                var bytes = jar.getInputStream(entry).readAllBytes();
                out.write(entry.getName().equals("org/ladysnake/impersonate/impl/mixin/PlayerManagerMixin.class") ? optionalStatsRedirect(bytes) : bytes);
                out.closeEntry();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("No arguments supported");
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path preview = root.resolve("reports/preview");
        prepareImpersonate(root);
        var names = names(root.resolve("test-libs/intermediary-mojang-1.21.1.tsv"));
        var destination = preview.resolve("bewitchment-v35-neoforge-local-only.jar");
        Files.deleteIfExists(destination);
        var rewritten = new int[1];
        try (var jar = new ZipFile(preview.resolve("bewitchment-v34-local-only.jar").toFile());
             var out = new ZipOutputStream(Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW))) {
            JsonObject refmap;
            try (var input = jar.getInputStream(jar.getEntry("bewitchment-refmap.json"))) {
                refmap = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("mappings");
            }
            for (var entry : Collections.list(jar.entries())) {
                if (entry.isDirectory()) continue;
                byte[] data;
                try (var input = jar.getInputStream(entry)) {
                    data = input.readAllBytes();
                }
                boolean mixin = entry.getName().startsWith("moriyashiine/bewitchment/mixin/") && entry.getName().endsWith(".class");
                out.putNextEntry(new ZipEntry(entry.getName()));
                if (ATLAS_READERS.contains(entry.getName())) data = inlineAtlases(data);
                out.write(mixin ? mixinClass(entry.getName(), data, names, refmap, rewritten) : data);
                out.closeEntry();
            }
        }
        Files.copy(root.resolve("fabric/build/libs/rewitchment-0.1.0.jar"),
                preview.resolve("rewitchment-neoforge.jar"), StandardCopyOption.REPLACE_EXISTING);
        System.out.println("NeoForge compatibility prepared, literal selectors rewritten: " + rewritten[0]);
    }
}
