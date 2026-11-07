package com.veeda.rewitchment.translator;

import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class GameplayCompletionAdapter implements Opcodes {
    static final String P = "moriyashiine/bewitchment/";
    static final String HELPER = "com/veeda/rewitchment/probe/LegacyGameplay", NBT = "com/veeda/rewitchment/probe/LegacyLiveNbt";
    static final String HOOKS = "com/veeda/rewitchment/probe/LegacyMixinHooks";
    static final String MC = "net/minecraft/";
    static final String STACK = "L" + MC + "class_1799;", COMPOUND = "L" + MC + "class_2487;", LIVING = "L" + MC + "class_1309;", ENTITY = "L" + MC + "class_1297;";
    static final String WORLD = "L" + MC + "class_1937;", RANDOM = "L" + MC + "class_5819;", ATTRIBUTES = "L" + MC + "class_5131;", EFFECT_OWNER = MC + "class_1291";
    static final String GROUP = "com/veeda/rewitchment/probe/LegacyEntityGroup";
    static final String ENCHANTMENTS = P + "common/registry/BWEnchantments";
    static final String EMPTY = "()V";

    static final String APPLIED_OLD = "(" + LIVING + ATTRIBUTES + "I)V", APPLIED_ENTITY = "(" + LIVING + "I)V";

    private GameplayCompletionAdapter() {}

    public record Scan(Set<String> tamed) {}

    public static Scan scan(Collection<ClassNode> classes) {
        var tamed = new TreeSet<String>();
        for (var node : classes) for (var method : node.methods)
            if (method.name.equals("method_6173") && method.desc.equals("(Z)V")) tamed.add(node.name);
        return new Scan(tamed);
    }

    private static void count(Map<String, Integer> counts, String key) { counts.merge(key, 1, Integer::sum); }
    static AbstractInsnNode previous(AbstractInsnNode insn) {
        do insn = insn.getPrevious(); while (insn != null && insn.getOpcode() < 0);
        return insn;
    }
    static AbstractInsnNode next(AbstractInsnNode insn) {
        do insn = insn.getNext(); while (insn != null && insn.getOpcode() < 0);
        return insn;
    }
    private static boolean has(ClassNode node, String name, String descriptor) {
        return node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor));
    }
    private static MethodInsnNode helper(String name, String descriptor) {
        return new MethodInsnNode(INVOKESTATIC, HELPER, name, descriptor, false);
    }

    private static void factory(MethodNode method, MethodInsnNode call, String name, String descriptor) {
        AbstractInsnNode cursor = call;
        while (!(cursor instanceof TypeInsnNode type && type.getOpcode() == NEW && type.desc.equals(call.owner))) {
            cursor = cursor.getPrevious();
            if (cursor == null) throw new IllegalStateException("No allocation for " + call.owner);
        }
        var duplicate = next(cursor);
        if (duplicate.getOpcode() != DUP) throw new IllegalStateException("Unexpected allocation shape");
        method.instructions.remove(duplicate);
        method.instructions.remove(cursor);
        method.instructions.set(call, helper(name, descriptor));
    }

    private static boolean shadowCalls(MethodNode method, MethodInsnNode call, Map<String, Integer> counts) {
        if (!call.owner.startsWith(P + "mixin/") || call.getOpcode() != INVOKEVIRTUAL && call.getOpcode() != INVOKESPECIAL) return false;
        if (call.name.equals("method_16943")) {
            call.name = "method_7495";
            count(counts, "shadow_call_redirects");
            return true;
        }
        var effect = "L" + EFFECT_OWNER + ";";
        var effects = "com/veeda/rewitchment/probe/LegacyStatusEffects";
        var replacement = switch (call.name + call.desc) {
            case String key when key.equals("method_6016(" + effect + ")Z") -> new MethodInsnNode(INVOKESTATIC, effects, "removeStatusEffect", "(" + LIVING + effect + ")Z", false);
            case String key when key.equals("method_6059(" + effect + ")Z") -> new MethodInsnNode(INVOKESTATIC, effects, "hasStatusEffect", "(" + LIVING + effect + ")Z", false);
            case String key when key.equals("method_6112(" + effect + ")L" + MC + "class_1293;") -> new MethodInsnNode(INVOKESTATIC, effects, "getStatusEffect", "(" + LIVING + effect + ")L" + MC + "class_1293;", false);
            case String key when key.equals("method_26825(L" + MC + "class_1320;)D") -> helper("attributeValue", "(" + LIVING + "L" + MC + "class_1320;)D");
            case String key when key.equals("method_7969()" + COMPOUND) -> new MethodInsnNode(INVOKESTATIC, NBT, "get", "(" + STACK + ")" + COMPOUND, false);
            case String key when key.equals("method_7948()" + COMPOUND) -> new MethodInsnNode(INVOKESTATIC, NBT, "getOrCreate", "(" + STACK + ")" + COMPOUND, false);
            default -> null;
        };
        if (replacement == null) return false;
        method.instructions.set(call, replacement);
        count(counts, "shadow_call_redirects");
        return true;
    }

    private static void calls(ClassNode node, MethodNode method, MethodInsnNode call, Scan scan, Map<String, Integer> counts) {
        if (shadowCalls(method, call, counts)) return;
        if (node.name.equals(P + "api/component/TransformationComponent") && method.name.equals("updateAttributes") &&
                call.owner.equals(MC + "class_1324") && call.name.equals("method_26837") && call.desc.equals("(L" + MC + "class_1322;)V")) {
            method.instructions.set(call, helper("replaceTransformationModifier", "(L" + MC + "class_1324;L" + MC + "class_1322;)V"));
            count(counts, "transformation_modifier_replacements");
            return;
        }

        if (call.name.equals("method_5639") && call.desc.equals("(I)V")) {
            method.instructions.insertBefore(call, new InsnNode(I2F));
            call.desc = "(F)V";
            count(counts, "fire_seconds_calls");
            return;
        }
        var code = method.instructions;
        var key = call.owner + "." + call.name;
        switch (key) {
            case MC + "class_1799.method_7948" -> {
                var after = next(call);
                if (after instanceof LdcInsnNode ldc && "SkullOwner".equals(ldc.cst)) {
                    var cursor = after;
                    while (!(cursor instanceof MethodInsnNode store && store.owner.equals(MC + "class_2487"))) cursor = next(cursor);
                    code.remove(after);
                    code.remove(call);
                    code.set(cursor, helper("setSkullOwner", "(" + STACK + "Ljava/lang/String;)V"));
                    count(counts, "skull_owner_profiles");
                } else {
                    code.set(call, new MethodInsnNode(INVOKESTATIC, NBT, "getOrCreate", "(" + STACK + ")" + COMPOUND, false));
                    count(counts, "live_nbt_views");
                }
            }
            case MC + "class_1799.method_7969" -> {
                code.set(call, new MethodInsnNode(INVOKESTATIC, NBT, "get", "(" + STACK + ")" + COMPOUND, false));
                count(counts, "live_nbt_views");
            }
            case MC + "class_1799.method_7970" -> {
                if (!call.desc.equals("(I" + RANDOM + "L" + MC + "class_3222;)Z")) return;
                code.set(call, helper("damage", "(" + STACK + "I" + RANDOM + "L" + MC + "class_3222;)Z"));
                count(counts, "random_durability_calls");
            }
            case MC + "class_1297.method_20620" -> {
                if (!call.desc.equals("(DDD)V")) return;
                call.name = "method_5859";
                count(counts, "teleport_requests");
            }
            case MC + "class_1350.<init>" -> {
                if (!call.desc.equals("(L" + MC + "class_1321;DFFZ)V")) return;
                var flag = previous(call);
                if (flag.getOpcode() != ICONST_0) throw new IllegalStateException("Unexpected follow flag in " + node.name);
                code.remove(flag);
                call.desc = "(L" + MC + "class_1321;DFF)V";
                count(counts, "follow_goals");
            }
            case MC + "class_1674.<init>" -> {
                if (!call.desc.equals("(" + WORLD + LIVING + "DDDI)V")) return;
                factory(method, call, "fireball", "(" + WORLD + LIVING + "DDDI)L" + MC + "class_1674;");
                count(counts, "projectile_factories");
            }
            case MC + "class_1677.<init>" -> {
                if (!call.desc.equals("(" + WORLD + LIVING + "DDD)V")) return;
                factory(method, call, "smallFireball", "(" + WORLD + LIVING + "DDD)L" + MC + "class_1677;");
                count(counts, "projectile_factories");
            }
            case MC + "class_1687.<init>" -> {
                if (!call.desc.equals("(" + WORLD + LIVING + "DDD)V")) return;
                factory(method, call, "witherSkull", "(" + WORLD + LIVING + "DDD)L" + MC + "class_1687;");
                count(counts, "projectile_factories");
            }
            case MC + "class_1890.method_8210" -> {

                code.set(call, new InsnNode(POP2));
                count(counts, "enchantment_combat_calls");
            }
            case MC + "class_1890.method_8213" -> {
                code.set(call, helper("targetDamaged", "(" + LIVING + ENTITY + ")V"));
                count(counts, "enchantment_combat_calls");
            }
            case MC + "class_1890.method_8218" -> {
                if (!(previous(call) instanceof MethodInsnNode group) || !group.owner.equals(GROUP)) throw new IllegalStateException("Unexpected attack bonus");
                code.remove(group);
                code.set(call, helper("attackBonus", "(" + STACK + LIVING + ")F"));
                count(counts, "enchantment_combat_calls");
            }
            case MC + "class_1890.method_8226" -> {
                code.set(call, helper("looting", "(" + LIVING + ")I"));
                count(counts, "enchantment_queries");
            }
            case MC + "class_1890.method_8233" -> {
                if (!call.desc.equals("(" + RANDOM + STACK + "IZ)" + STACK)) return;
                int world = -1;
                for (var insn : method.instructions.toArray())
                    if (insn instanceof VarInsnNode load && load.getOpcode() == ALOAD && load.var == 1) world = 1;
                if (world < 0 || !method.desc.startsWith("(" + WORLD) && !method.desc.startsWith("(L" + MC + "class_3218;")) throw new IllegalStateException("No world for enchant");
                code.insertBefore(call, new VarInsnNode(ALOAD, 1));
                code.set(call, helper("enchant", "(" + RANDOM + STACK + "IZ" + WORLD + ")" + STACK));
                count(counts, "enchantment_queries");
            }
            case "com/veeda/rewitchment/probe/LegacyEnchantmentQueries.getLegacyLevel" -> {
                if (!(previous(previous(call)) instanceof FieldInsnNode field) || !field.owner.equals(ENCHANTMENTS)) return;
                code.remove(field);
                code.set(call, helper("magicProtection", "(" + STACK + ")I"));
                count(counts, "enchantment_queries");
            }
            case MC + "class_2256.method_9651" -> {
                if (!call.desc.endsWith("Z)Z")) return;
                code.insertBefore(call, new InsnNode(POP));
                call.desc = call.desc.replace("Z)Z", ")Z");
                count(counts, "fertilizable_queries");
            }
            case MC + "class_2263.method_9700" -> {
                if (!call.desc.startsWith("(L" + MC + "class_1936;")) return;
                code.set(call, helper("drain", "(L" + MC + "class_2263;L" + MC + "class_1936;L" + MC + "class_2338;L" + MC + "class_2680;)" + STACK));
                count(counts, "fluid_drains");
            }
            case MC + "class_2631.method_11335" -> {
                code.set(call, helper("resolveProfile", "(Lcom/mojang/authlib/GameProfile;Ljava/util/function/Consumer;)V"));
                count(counts, "profile_lookups");
            }
            case MC + "class_3222.<init>" -> {
                if (!call.desc.equals("(Lnet/minecraft/server/MinecraftServer;L" + MC + "class_3218;Lcom/mojang/authlib/GameProfile;)V")) return;
                factory(method, call, "fakePlayer", "(Lnet/minecraft/server/MinecraftServer;L" + MC + "class_3218;Lcom/mojang/authlib/GameProfile;)L" + MC + "class_3222;");
                count(counts, "fake_player_factories");
            }
            case MC + "class_3244.<init>" -> {
                if (!call.desc.equals("(Lnet/minecraft/server/MinecraftServer;L" + MC + "class_2535;L" + MC + "class_3222;)V")) return;
                factory(method, call, "fakeHandler", "(Lnet/minecraft/server/MinecraftServer;L" + MC + "class_2535;L" + MC + "class_3222;)L" + MC + "class_3244;");
                count(counts, "fake_player_factories");
            }
            default -> superCalls(method, call, scan, counts);
        }
    }

    private static void superCalls(MethodNode method, MethodInsnNode call, Scan scan, Map<String, Integer> counts) {
        if (call.getOpcode() != INVOKESPECIAL) return;
        var code = method.instructions;
        if (call.name.equals("method_5555") && call.desc.equals(APPLIED_OLD)) {
            code.insertBefore(call, new InsnNode(DUP2_X1));
            code.insertBefore(call, new InsnNode(POP2));
            code.insertBefore(call, new InsnNode(POP));
            call.desc = "(" + ATTRIBUTES + "I)V";
            count(counts, "effect_super_calls");
        } else if (call.name.equals("method_5562") && call.desc.equals(APPLIED_OLD)) {
            code.insertBefore(call, new InsnNode(POP));
            code.insertBefore(call, new InsnNode(SWAP));
            code.insertBefore(call, new InsnNode(POP));
            call.desc = "(" + ATTRIBUTES + ")V";
            count(counts, "effect_super_calls");
        } else if (call.name.equals("method_6173") && call.desc.equals("(Z)V") && !scan.tamed.contains(call.owner)) {
            code.insertBefore(call, new InsnNode(ICONST_1));
            call.desc = "(ZZ)V";
            count(counts, "tamed_super_calls");
        } else if (call.name.equals("method_5759") && call.desc.equals("(DDDFFIZ)V")) {
            code.insertBefore(call, new InsnNode(POP));
            call.desc = "(DDDFFI)V";
            count(counts, "tracked_position_super_calls");
        }
    }

    private static void fields(MethodNode method, Map<String, Integer> counts) {
        for (var insn : method.instructions.toArray()) {
            if (!(insn instanceof FieldInsnNode field) || !field.owner.equals(MC + "class_3417") || !field.name.equals("field_15152")) continue;
            field.desc = "L" + MC + "class_6880$class_6883;";
            var code = new InsnList();
            code.add(new MethodInsnNode(INVOKEINTERFACE, MC + "class_6880", "comp_349", "()Ljava/lang/Object;", true));
            code.add(new TypeInsnNode(CHECKCAST, MC + "class_3414"));
            method.instructions.insert(field, code);
            count(counts, "registry_sound_reads");
        }
    }

    private static MethodNode bridge(ClassNode node, String name, String descriptor, String oldDescriptor, InsnList arguments, InsnList returns, int stack, int locals) {
        var method = new MethodNode(ACC_PUBLIC, name, descriptor, null, null);
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(arguments);
        method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, node.name, name, oldDescriptor, false));
        method.instructions.add(returns);
        method.maxStack = stack;
        method.maxLocals = locals;
        node.methods.add(method);
        return method;
    }

    private static InsnList loads(int... opcodeSlotPairs) {
        var list = new InsnList();
        for (int i = 0; i < opcodeSlotPairs.length; i += 2) list.add(new VarInsnNode(opcodeSlotPairs[i], opcodeSlotPairs[i + 1]));
        return list;
    }
    private static InsnList single(int... opcodes) {
        var list = new InsnList();
        for (int opcode : opcodes) list.add(new InsnNode(opcode));
        return list;
    }

    private static void overrides(ClassNode node, Map<String, Integer> counts) {
        if (has(node, "method_5555", APPLIED_OLD) && !has(node, "method_52520", APPLIED_ENTITY)) {
            var method = new MethodNode(ACC_PUBLIC, "method_52520", APPLIED_ENTITY, null, null);
            var code = method.instructions;
            code.add(new VarInsnNode(ALOAD, 0));
            code.add(new VarInsnNode(ALOAD, 1));
            code.add(new VarInsnNode(ALOAD, 1));
            code.add(new MethodInsnNode(INVOKEVIRTUAL, MC + "class_1309", "method_6127", "()" + ATTRIBUTES, false));
            code.add(new VarInsnNode(ILOAD, 2));
            code.add(new MethodInsnNode(INVOKEVIRTUAL, node.name, "method_5555", APPLIED_OLD, false));
            code.add(new InsnNode(RETURN));
            method.maxStack = 4;
            method.maxLocals = 3;
            node.methods.add(method);
            count(counts, "effect_apply_bridges");
        }
        if (has(node, "method_5562", APPLIED_OLD) && !node.interfaces.contains(HOOKS + "$EffectRemoval")) {
            node.interfaces.add(HOOKS + "$EffectRemoval");
            var method = new MethodNode(ACC_PUBLIC, "bewitchment$onRemoved", APPLIED_OLD, null, null);
            var code = method.instructions;
            code.add(new VarInsnNode(ALOAD, 0));
            code.add(new VarInsnNode(ALOAD, 1));
            code.add(new VarInsnNode(ALOAD, 2));
            code.add(new VarInsnNode(ILOAD, 3));
            code.add(new MethodInsnNode(INVOKEVIRTUAL, node.name, "method_5562", APPLIED_OLD, false));
            code.add(new InsnNode(RETURN));
            method.maxStack = 4;
            method.maxLocals = 4;
            node.methods.add(method);
            count(counts, "effect_removal_bridges");
        }
        if (has(node, "method_6173", "(Z)V") && !has(node, "method_6173", "(ZZ)V")) {
            bridge(node, "method_6173", "(ZZ)V", "(Z)V", loads(ILOAD, 1), single(RETURN), 2, 3);
            count(counts, "tamed_bridges");
        }
        if (has(node, "method_5759", "(DDDFFIZ)V") && !has(node, "method_5759", "(DDDFFI)V")) {
            var arguments = loads(DLOAD, 1, DLOAD, 3, DLOAD, 5, FLOAD, 7, FLOAD, 8, ILOAD, 9);
            arguments.add(new InsnNode(ICONST_1));
            bridge(node, "method_5759", "(DDDFFI)V", "(DDDFFIZ)V", arguments, single(RETURN), 10, 10);
            count(counts, "tracked_position_bridges");
        }
        if (has(node, "method_5931", "(L" + MC + "class_1657;)Z") && !has(node, "method_5931", "()Z")) {
            var method = new MethodNode(ACC_PUBLIC, "method_5931", "()Z", null, null);
            method.instructions.add(new VarInsnNode(ALOAD, 0));
            method.instructions.add(new InsnNode(ACONST_NULL));
            method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, node.name, "method_5931", "(L" + MC + "class_1657;)Z", false));
            method.instructions.add(new InsnNode(IRETURN));
            method.maxStack = 2;
            method.maxLocals = 1;
            node.methods.add(method);
            count(counts, "leash_bridges");
        }
    }

    private static void enchantmentRegistry(ClassNode node, Map<String, Integer> counts) {
        if (!node.fields.removeIf(f -> f.name.equals("MAGIC_PROTECTION"))) return;
        node.methods.removeIf(m -> m.name.equals("<clinit>"));
        for (var method : node.methods) if (method.name.equals("init")) {
            method.instructions.clear();
            method.tryCatchBlocks.clear();
            method.localVariables = null;
            method.instructions.add(new InsnNode(RETURN));
        }
        node.innerClasses.clear();
        count(counts, "data_driven_enchantments");
    }

    public static Map<String, Integer> rewrite(ClassNode node, Scan scan) {
        var counts = new TreeMap<String, Integer>();
        if (!node.name.startsWith(P)) return counts;
        if (node.name.equals(ENCHANTMENTS)) enchantmentRegistry(node, counts);
        for (var method : node.methods) {
            for (var insn : method.instructions.toArray())
                if (insn instanceof MethodInsnNode call) calls(node, method, call, scan, counts);
            fields(method, counts);
        }
        if (!node.name.contains("/mixin/")) overrides(node, counts);
        return counts;
    }
}
