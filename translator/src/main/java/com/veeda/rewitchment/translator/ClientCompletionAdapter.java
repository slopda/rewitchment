package com.veeda.rewitchment.translator;

import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

public final class ClientCompletionAdapter implements Opcodes {
    static final String P = "moriyashiine/bewitchment/", MC = "net/minecraft/";
    static final String CLIENT = "com/veeda/rewitchment/probe/LegacyClientRendering";
    static final String GAMEPLAY = "com/veeda/rewitchment/probe/LegacyGameplay";
    static final String MATRIX = "L" + MC + "class_4587;", VERTICES = "L" + MC + "class_4588;";
    static final String MODEL_OLD = "(" + MATRIX + VERTICES + "IIFFFF)V", MODEL_NEW = "(" + MATRIX + VERTICES + "III)V";
    static final String WORLD = "L" + MC + "class_1937;", LOOKUP = "L" + MC + "class_7225$class_7874;";
    static final String VARIABLE = "vazkii/patchouli/api/IVariable", PROVIDER = "vazkii/patchouli/api/IVariableProvider";
    static final String SIGNS = "com/terraformersmc/terraform/sign/";

    private ClientCompletionAdapter() {}

    public record Scan(Set<String> models) {}

    public static Scan scan(Collection<ClassNode> classes) {
        var models = new TreeSet<String>();
        for (var node : classes) for (var method : node.methods)
            if (method.name.equals("method_2828") && method.desc.equals(MODEL_OLD)) models.add(node.name);
        return new Scan(models);
    }

    private static void count(Map<String, Integer> counts, String key) { counts.merge(key, 1, Integer::sum); }
    private static boolean has(ClassNode node, String name, String descriptor) {
        return node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor));
    }

    private static void modelBridge(ClassNode node, Map<String, Integer> counts) {
        var method = new MethodNode(ACC_PUBLIC, "method_2828", MODEL_NEW, null, null);
        var code = method.instructions;
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new VarInsnNode(ALOAD, 1));
        code.add(new VarInsnNode(ALOAD, 2));
        code.add(new VarInsnNode(ILOAD, 3));
        code.add(new VarInsnNode(ILOAD, 4));
        for (var channel : List.of("red", "green", "blue", "alpha")) {
            code.add(new VarInsnNode(ILOAD, 5));
            code.add(new MethodInsnNode(INVOKESTATIC, GAMEPLAY, channel, "(I)F", false));
        }
        code.add(new MethodInsnNode(INVOKEVIRTUAL, node.name, "method_2828", MODEL_OLD, false));
        code.add(new InsnNode(RETURN));
        method.maxStack = 9;
        method.maxLocals = 6;
        node.methods.add(method);
        count(counts, "model_render_bridges");
    }

    private static void zephyr(ClassNode node, Map<String, Integer> counts) {
        var trinket = "dev/emi/trinkets/api/TrinketItem";
        var slot = "Ldev/emi/trinkets/api/SlotReference;";
        var stack = "L" + MC + "class_1799;";
        var living = "L" + MC + "class_1309;";
        var id = "L" + MC + "class_2960;";
        var multimap = "Lcom/google/common/collect/Multimap;";
        var old = "(" + stack + slot + living + "Ljava/util/UUID;)" + multimap;
        var target = "(" + stack + slot + living + id + ")" + multimap;
        for (var method : node.methods) if (method.name.equals("getModifiers") && method.desc.equals(old)) {
            boolean original = Arrays.stream(method.instructions.toArray()).anyMatch(i -> i instanceof LdcInsnNode ldc && Double.valueOf(0.1D).equals(ldc.cst));
            if (!original) throw new IllegalStateException("Unexpected Zephyr modifier");
            method.desc = target;
            method.signature = null;
            method.localVariables = null;
            method.instructions.clear();
            method.tryCatchBlocks.clear();
            for (int slotIndex = 0; slotIndex <= 4; slotIndex++) method.instructions.add(new VarInsnNode(ALOAD, slotIndex));
            method.instructions.add(new MethodInsnNode(INVOKESPECIAL, trinket, "getModifiers", target, false));
            method.instructions.add(new VarInsnNode(ALOAD, 4));
            method.instructions.add(new MethodInsnNode(INVOKESTATIC, GAMEPLAY, "zephyrModifiers", "(" + multimap + id + ")" + multimap, false));
            method.instructions.add(new InsnNode(ARETURN));
            method.maxStack = 5;
            method.maxLocals = 5;
            count(counts, "trinket_modifier_overrides");
        }
    }

    public static ClassNode rewrite(ClassNode node, Scan scan, Map<String, Integer> counts) {
        if (!node.name.startsWith(P)) return node;
        boolean processor = node.name.contains("/integration/patchouli/");
        for (var method : node.methods) for (var insn : method.instructions.toArray()) {
            if (insn instanceof FieldInsnNode field && field.owner.equals(MC + "class_2398") && field.name.equals("field_11226")) {
                var cursor = field.getNext();
                while (!(cursor instanceof MethodInsnNode call && call.name.equals("method_8406"))) cursor = cursor.getNext();
                method.instructions.remove(field);
                method.instructions.set(cursor, new MethodInsnNode(INVOKESTATIC, CLIENT, "entityEffect", "(" + WORLD + "DDDDDD)V", false));
                count(counts, "entity_effect_particles");
            }
            if (!(insn instanceof MethodInsnNode call)) continue;
            if (node.name.equals(P + "client/screen/DemonScreen") && method.name.equals("drawPortrait") &&
                    call.owner.equals("com/mojang/blaze3d/systems/RenderSystem")) {
                if (call.name.equals("enableScissor") && call.desc.equals("(IIII)V")) {

                    boolean x = method.localVariables != null && method.localVariables.stream().anyMatch(v -> v.name.equals("x") && v.index == 4 && v.desc.equals("I"));
                    boolean y = method.localVariables != null && method.localVariables.stream().anyMatch(v -> v.name.equals("y") && v.index == 5 && v.desc.equals("I"));
                    if (!x || !y) throw new IllegalStateException("Demon portrait anchors changed");
                    method.instructions.insertBefore(call, new VarInsnNode(ALOAD, 1));
                    method.instructions.insertBefore(call, new VarInsnNode(ILOAD, 4));
                    method.instructions.insertBefore(call, new VarInsnNode(ILOAD, 5));
                    method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, CLIENT, "beginDemonPortrait", "(IIIILnet/minecraft/class_332;II)V", false));
                    method.maxStack += 3;
                    count(counts, "demon_portrait_clip");
                } else if (call.name.equals("disableScissor") && call.desc.equals("()V")) {
                    method.instructions.insertBefore(call, new VarInsnNode(ALOAD, 1));
                    method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, CLIENT, "endDemonPortrait", "(Lnet/minecraft/class_332;)V", false));
                    count(counts, "demon_portrait_clip_end");
                }
            }
            if (node.name.equals(P + "client/screen/DemonScreen") && method.name.equals("drawHearts") &&
                    call.owner.equals(MC + "class_332") && call.name.equals("method_25302") &&
                    call.desc.equals("(L" + MC + "class_2960;IIIIII)V")) {
                method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, CLIENT, "drawDemonHeart",
                        "(L" + MC + "class_332;" + call.desc.substring(1), false));
                count(counts, "demon_heart_sprites");
            } else if (call.getOpcode() == INVOKESPECIAL && call.name.equals("method_2828") && call.desc.equals(MODEL_OLD) && !scan.models.contains(call.owner)) {
                method.instructions.insertBefore(call, new MethodInsnNode(INVOKESTATIC, GAMEPLAY, "packColor", "(FFFF)I", false));
                call.desc = MODEL_NEW;
                count(counts, "model_super_calls");
            } else if (call.owner.equals(MC + "class_927") && call.name.equals("method_4072")) {
                call.owner = MC + "class_922";
                call.name = "method_4054";
                call.desc = call.desc.replace("L" + MC + "class_1308;", "L" + MC + "class_1309;");
                count(counts, "mob_renderer_super_calls");
            } else if (call.owner.equals(MC + "class_490") && call.name.equals("method_2486") && call.desc.startsWith("(L" + MC + "class_332;IIIFF")) {
                method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, CLIENT, "drawEntity", call.desc, false));
                count(counts, "inventory_entity_draws");
            } else if (call.name.equals("method_25420") && call.desc.equals("(L" + MC + "class_332;)V")) {
                method.instructions.insertBefore(call, new VarInsnNode(ILOAD, 2));
                method.instructions.insertBefore(call, new VarInsnNode(ILOAD, 3));
                method.instructions.insertBefore(call, new VarInsnNode(FLOAD, 4));
                call.desc = "(L" + MC + "class_332;IIF)V";
                count(counts, "screen_backgrounds");
            } else if (processor && (call.owner.equals(VARIABLE) && call.name.equals("from") || call.owner.equals(PROVIDER) && call.name.equals("get"))) {
                if (!method.desc.startsWith("(" + WORLD)) throw new IllegalStateException("No world in " + method.name);
                method.instructions.insertBefore(call, new VarInsnNode(ALOAD, 1));
                method.instructions.insertBefore(call, new MethodInsnNode(INVOKEVIRTUAL, MC + "class_1937", "method_30349", "()L" + MC + "class_5455;", false));
                call.desc = call.desc.replace(")L" + VARIABLE + ";", LOOKUP + ")L" + VARIABLE + ";");
                count(counts, "patchouli_variable_calls");
            }
        }
        if (scan.models.contains(node.name) && !has(node, "method_2828", MODEL_NEW)) modelBridge(node, counts);
        if (node.name.endsWith("/ZephyrHarnessItem")) zephyr(node, counts);
        var callback = "(Ljava/util/function/UnaryOperator;" + LOOKUP + ")V";
        if (has(node, "onVariablesAvailable", "(Ljava/util/function/UnaryOperator;)V") && !has(node, "onVariablesAvailable", callback)) {
            var method = new MethodNode(ACC_PUBLIC, "onVariablesAvailable", callback, null, null);
            method.instructions.add(new VarInsnNode(ALOAD, 0));
            method.instructions.add(new VarInsnNode(ALOAD, 1));
            method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, node.name, "onVariablesAvailable", "(Ljava/util/function/UnaryOperator;)V", false));
            method.instructions.add(new InsnNode(RETURN));
            method.maxStack = 2;
            method.maxLocals = 3;
            node.methods.add(method);
            count(counts, "patchouli_callbacks");
        }
        var scanNode = new ClassNode();
        node.accept(scanNode);
        var writer = new org.objectweb.asm.ClassWriter(0);
        scanNode.accept(writer);
        if (new String(writer.toByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1).contains(SIGNS + "SpriteIdentifierRegistry")) {
            var result = new ClassNode();
            node.accept(new ClassRemapper(result, new SimpleRemapper(SIGNS + "SpriteIdentifierRegistry", SIGNS + "api/SpriteIdentifierRegistry")));
            count(counts, "sign_sprite_relocations");
            return result;
        }
        return node;
    }
}
