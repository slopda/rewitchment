package com.veeda.rewitchment.translator;

import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

public final class BlockStartupAdapter implements Opcodes {
    static final String P = "moriyashiine/bewitchment/";
    static final String HELPER = "com/veeda/rewitchment/probe/LegacyBlockStartup";
    static final String S = "Lnet/minecraft/class_4970$class_2251;", SET = "Lnet/minecraft/class_8177;";
    static final String RULE_OWNER = "net/minecraft/class_2440$class_2441", RULE = "L" + RULE_OWNER + ";";
    static final String STATE = "Lnet/minecraft/class_2680;", WORLD = "Lnet/minecraft/class_1937;", POS = "Lnet/minecraft/class_2338;";
    static final String PLAYER = "Lnet/minecraft/class_1657;", HAND = "Lnet/minecraft/class_1268;", HIT = "Lnet/minecraft/class_3965;";
    static final String ACTION = "Lnet/minecraft/class_1269;", ITEM_ACTION = "Lnet/minecraft/class_9062;";
    static final String STACK = "Lnet/minecraft/class_1799;", BLOCK = "Lnet/minecraft/class_2248;", ITEM = "Lnet/minecraft/class_1792;";
    static final String OLD_USE_NAME = "method_9534", NEW_USE_NAME = "method_55766", WITH_ITEM_NAME = "method_55765";
    static final String OLD_USE = "(" + STATE + WORLD + POS + PLAYER + HAND + HIT + ")" + ACTION;
    static final String NEW_USE = "(" + STATE + WORLD + POS + PLAYER + HIT + ")" + ACTION;
    static final String WITH_ITEM = "(" + STACK + STATE + WORLD + POS + PLAYER + HAND + HIT + ")" + ITEM_ACTION;
    static final String PICK_NAME = "method_9574";
    static final String PICK_OLD = "(Lnet/minecraft/class_1922;" + POS + STATE + ")" + STACK;
    static final String PICK_NEW = "(Lnet/minecraft/class_4538;" + POS + STATE + ")" + STACK;
    static final String CODEC_NAME = "method_53969", CODEC_DESC = "()Lcom/mojang/serialization/MapCodec;";
    static final String OLD_GENERATOR = "net/minecraft/class_2647", NEW_GENERATOR = "net/minecraft/class_8813";
    static final String GENERATORS = P + "common/world/generator/tree/generator/";
    static final String WORLD_GENERATORS = P + "common/registry/BWWorldGenerators";
    static final String FOOD = "net/minecraft/class_4174", FOOD_BUILDER = FOOD + "$class_4175";
    static final String POINTER = "net/minecraft/class_2342";
    static final String SETTINGS_BUILDER = "net/fabricmc/fabric/api/object/builder/v1/block/FabricBlockSettings";
    static final String ACCESSOR = P + "mixin/BlocksAccessor";
    static final String SAPLING_BLOCK = P + "common/block/util/BWSaplingBlock", OBJECTS = P + "common/registry/BWObjects";
    static final String SIGN = "com/terraformersmc/terraform/sign/";

    public static final Map<String, String> SAPLINGS = Map.of(GENERATORS + "JuniperSaplingGenerator", "JUNIPER",
            GENERATORS + "CypressSaplingGenerator", "CYPRESS", GENERATORS + "ElderSaplingGenerator", "ELDER",
            GENERATORS + "DragonsBloodSaplingGenerator", "DRAGONS_BLOOD");
    static final Set<String> CODEC_GAPS = Set.of(P + "api/block/PoppetShelfBlock", P + "api/block/WitchAltarBlock",
            P + "common/block/GlyphBlock", P + "common/block/SigilBlock");
    static final String PROJECTILE = "net/minecraft/class_1665", PROJECTILE_ENTITY_TYPE = "Lnet/minecraft/class_1299;";
    static final String PROJECTILE_AT = "(" + PROJECTILE_ENTITY_TYPE + "DDD" + WORLD + ")V";
    static final String PROJECTILE_OWNED = "(" + PROJECTILE_ENTITY_TYPE + "Lnet/minecraft/class_1309;" + WORLD + ")V";
    static final String DEFAULT_STACK = "method_57314";
    static final Map<String, String> PROJECTILES = Map.of(P + "common/entity/projectile/SilverArrowEntity", "SILVER_ARROW",
            P + "common/entity/projectile/HornedSpearEntity", "HORNED_SPEAR");
    static final String ARROW_ITEM = P + "common/item/SilverArrowItem", ARROW_NAME = "method_7702";
    static final String ARROW_OLD = "(" + WORLD + STACK + "Lnet/minecraft/class_1309;)L" + PROJECTILE + ";";
    static final String ARROW_NEW = "(" + WORLD + STACK + "Lnet/minecraft/class_1309;" + STACK + ")L" + PROJECTILE + ";";
    static final String OLD_DISPENSER = "net/minecraft/class_2965", DISPENSER = "com/veeda/rewitchment/probe/LegacyProjectileDispenser";
    static final String EFFECT = "Lnet/minecraft/class_1291;";
    static final String EFFECT_MODIFIER = "(Lnet/minecraft/class_1320;Ljava/lang/String;DLnet/minecraft/class_1322$class_1323;)" + EFFECT;
    static final String EFFECT_TICK = "method_5572", EFFECT_TICK_OLD = "(Lnet/minecraft/class_1309;I)V", EFFECT_TICK_NEW = "(Lnet/minecraft/class_1309;I)Z";
    private static final Set<String> REMOVED_INNER_CLASSES = Set.of(RULE_OWNER, "net/minecraft/class_1317$class_1319");
    static final String OLD_SPAWN_LOCATION = "net/minecraft/class_1317$class_1319";
    static final String LOOT_EVENTS = "net/fabricmc/fabric/api/loot/v2/LootTableEvents";
    static final String OLD_GROUP = "net/minecraft/class_1310", GROUP = "com/veeda/rewitchment/probe/LegacyEntityGroup", GROUP_GETTER = "method_6046";
    private static final Map<String, String> GROUP_FIELDS = Map.of("field_6290", "DEFAULT", "field_6289", "UNDEAD",
            "field_6293", "ARTHROPOD", "field_6291", "ILLAGER", "field_6292", "AQUATIC");
    static final String WEREWOLF = P + "common/entity/living/WerewolfEntity";
    private static final Map<String, String> LIBRARY_ATTRIBUTES = Map.of(
            "com/jamieswhiteshirt/reachentityattributes/ReachEntityAttributes.ATTACK_RANGE", "attackRange",
            "com/jamieswhiteshirt/reachentityattributes/ReachEntityAttributes.REACH", "blockReach",
            "dev/emi/stepheightentityattribute/StepHeightEntityAttributeMain.STEP_HEIGHT", "stepHeight");
    private static final Map<String, String[]> SWAPPED = Map.of(
            "net/minecraft/class_2323", new String[] {"(" + S + SET + ")V", "(" + SET + S + ")V"},
            "net/minecraft/class_2533", new String[] {"(" + S + SET + ")V", "(" + SET + S + ")V"},
            "net/minecraft/class_2349", new String[] {"(" + S + "Lnet/minecraft/class_4719;)V", "(Lnet/minecraft/class_4719;" + S + ")V"},
            "net/minecraft/class_2431", new String[] {"(" + S + "Lnet/minecraft/class_6017;)V", "(Lnet/minecraft/class_6017;" + S + ")V"});
    private static final Map<String, String> FEATURE_KEYS = Map.of("net/minecraft/class_6803.method_46852", "configuredFeature",
            "net/minecraft/class_6817.method_46865", "placedFeature");
    private static final Map<String, String> POINTERS = Map.of("method_10207", "comp_1967:()Lnet/minecraft/class_3218;",
            "method_10122", "comp_1968:()" + POS, "method_10120", "comp_1969:()" + STATE);
    public static final Map<String, Integer> EXPECTED_COUNTS = Map.ofEntries(Map.entry("button_constructors", 4),
            Map.entry("door_trapdoor_gate_constructors", 12), Map.entry("experience_constructors", 2),
            Map.entry("pressure_plate_constructors", 4), Map.entry("sapling_generators", 4), Map.entry("sapling_constructor_signatures", 2),
            Map.entry("use_with_item_bridges", 27), Map.entry("use_super_calls", 23), Map.entry("dispenser_use_calls", 1),
            Map.entry("pick_stack_overrides", 3), Map.entry("pick_stack_super_calls", 1), Map.entry("block_codecs", 4),
            Map.entry("meat_flags_removed", 1), Map.entry("item_food_lookups", 3), Map.entry("nutrition_reads", 2),
            Map.entry("saturation_modifier_reads", 1), Map.entry("meat_tag_queries", 1), Map.entry("block_pointer_reads", 3),
            Map.entry("leaf_factories", 4), Map.entry("loot_table_keys", 2), Map.entry("sign_library_relocations", 1),
            Map.entry("feature_keys", 11),
            Map.entry("projectile_constructors", 4), Map.entry("default_item_stacks", 2), Map.entry("dispenser_bases", 1), Map.entry("arrow_bridges", 1),
            Map.entry("stale_uninitialized_frame_values", 40),
            Map.entry("library_attribute_reads", 5), Map.entry("absolute_step_defaults", 1),
            Map.entry("effect_attribute_modifiers", 3), Map.entry("effect_tick_bridges", 11),
            Map.entry("spawn_locations", 8), Map.entry("spawn_restrictions", 8), Map.entry("loot_registrations", 1), Map.entry("obsolete_loot_lambdas", 1),
            Map.entry("effect_reservations", 1), Map.entry("effect_registrations_deferred", 1), Map.entry("entity_group_queries", 4),
            Map.entry("entity_group_providers", 9), Map.entry("entity_group_classes", 12), Map.entry("obsolete_inner_class_records", 5));

    public record Scan(Set<String> useOverrides) {}
    public record Result(ClassNode node, Map<String, Integer> counts) {}
    private BlockStartupAdapter() {}

    public static Scan scan(Collection<ClassNode> classes) {
        var overrides = new TreeSet<String>();
        for (var node : classes) for (var method : node.methods)
            if (method.name.equals(OLD_USE_NAME) && method.desc.equals(OLD_USE)) overrides.add(node.name);
        return new Scan(overrides);
    }

    private static AbstractInsnNode previous(AbstractInsnNode insn) {
        do insn = insn.getPrevious(); while (insn != null && insn.getOpcode() < 0);
        return insn;
    }
    private static void count(Map<String, Integer> counts, String key) { counts.merge(key, 1, Integer::sum); }
    private static boolean constant(AbstractInsnNode insn, String owner, String name) {
        return insn instanceof FieldInsnNode field && field.getOpcode() == GETSTATIC
                && field.owner.equals(owner) && field.name.equals(name);
    }
    private static void reject(ClassNode node, MethodNode method, String why) {
        throw new IllegalStateException(why + " in " + node.name + "." + method.name);
    }

    private static boolean constructors(ClassNode node, MethodNode method, MethodInsnNode call, Map<String, Integer> counts) {
        if (call.getOpcode() != INVOKESPECIAL || !call.name.equals("<init>")) return false;
        var swapped = SWAPPED.get(call.owner);
        if (swapped != null && call.desc.equals(swapped[0])) {
            method.instructions.insertBefore(call, new InsnNode(SWAP));
            call.desc = swapped[1];
            count(counts, call.owner.equals("net/minecraft/class_2431") ? "experience_constructors" : "door_trapdoor_gate_constructors");
            return true;
        }
        if (call.owner.equals("net/minecraft/class_2269") && call.desc.equals("(" + S + SET + "IZ)V")) {

            var flag = previous(call);
            var ticks = previous(flag);
            var type = previous(ticks);
            if (flag.getOpcode() != ICONST_1 || !(ticks instanceof IntInsnNode time && time.operand == 30)
                    || !constant(type, "net/minecraft/class_8177", "field_42823")) reject(node, method, "Unexpected button values");
            method.instructions.remove(flag);
            method.instructions.insertBefore(call, new InsnNode(DUP2_X1));
            method.instructions.insertBefore(call, new InsnNode(POP2));
            call.desc = "(" + SET + "I" + S + ")V";
            count(counts, "button_constructors");
            return true;
        }
        if (call.owner.equals("net/minecraft/class_2440") && call.desc.equals("(" + RULE + S + SET + ")V")) {
            if (!constant(previous(call), "net/minecraft/class_8177", "field_42823")) reject(node, method, "Unexpected plate set");
            AbstractInsnNode rule = call;
            while (rule != null && !(rule instanceof FieldInsnNode field && field.desc.equals(RULE))) rule = rule.getPrevious();

            if (!constant(rule, RULE_OWNER, "field_11361")) reject(node, method, "Unexpected plate rule");
            method.instructions.remove(rule);
            method.instructions.insertBefore(call, new InsnNode(SWAP));
            call.desc = "(" + SET + S + ")V";
            count(counts, "pressure_plate_constructors");
            return true;
        }
        return false;
    }

    private static boolean projectiles(ClassNode node, MethodNode method, MethodInsnNode call, Map<String, Integer> counts) {
        var item = PROJECTILES.get(node.name);
        if (item == null || call.getOpcode() != INVOKESPECIAL || !call.owner.equals(PROJECTILE) || !call.name.equals("<init>")) return false;
        boolean located = call.desc.equals(PROJECTILE_AT), owned = call.desc.equals(PROJECTILE_OWNED);
        if (!located && !owned) return false;
        var code = new InsnList();
        if (owned && method.desc.endsWith(STACK + ")V")) code.add(new VarInsnNode(ALOAD, 4));
        else code.add(defaultStack(item));

        code.add(new InsnNode(ACONST_NULL));
        method.instructions.insertBefore(call, code);
        call.desc = call.desc.replace(")V", STACK + STACK + ")V");
        count(counts, "projectile_constructors");
        return true;
    }

    private static InsnList defaultStack(String item) {
        var code = new InsnList();
        code.add(new FieldInsnNode(GETSTATIC, OBJECTS, item, ITEM));
        code.add(new MethodInsnNode(INVOKESTATIC, HELPER, "stack", "(" + ITEM + ")" + STACK, false));
        return code;
    }

    private static void addDefaultStack(ClassNode node, String item, Map<String, Integer> counts) {

        var method = new MethodNode(ACC_PROTECTED, DEFAULT_STACK, "()" + STACK, null, null);
        method.instructions.add(defaultStack(item));
        method.instructions.add(new InsnNode(ARETURN));
        method.maxStack = 1;
        method.maxLocals = 1;
        node.methods.add(method);
        count(counts, "default_item_stacks");
    }

    private static void rebaseDispenser(ClassNode node, Map<String, Integer> counts) {

        if (!node.superName.equals(OLD_DISPENSER)) return;
        node.superName = DISPENSER;
        for (var method : node.methods) {
            if (method.name.equals("method_12844")) method.name = "createProjectile";
            for (var insn : method.instructions.toArray())
                if (insn instanceof MethodInsnNode call && call.owner.equals(OLD_DISPENSER)) call.owner = DISPENSER;
        }
        count(counts, "dispenser_bases");
    }

    private static void addArrowBridge(ClassNode node, Map<String, Integer> counts) {
        var method = new MethodNode(ACC_PUBLIC, ARROW_NAME, ARROW_NEW, null, null);
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        for (int slot = 1; slot <= 3; slot++) method.instructions.add(new VarInsnNode(ALOAD, slot));
        method.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, node.name, ARROW_NAME, ARROW_OLD, false));
        method.instructions.add(new InsnNode(ARETURN));
        method.maxStack = 4;
        method.maxLocals = 5;
        node.methods.add(method);
        count(counts, "arrow_bridges");
    }

    private static boolean saplings(MethodNode method, MethodInsnNode call, Map<String, Integer> counts) {
        var field = SAPLINGS.get(call.owner);
        if (field == null || !call.name.equals("<init>") || !call.desc.equals("()V") || call.getOpcode() != INVOKESPECIAL) return false;
        var duplicate = previous(call);
        var allocation = previous(duplicate);
        if (duplicate.getOpcode() != DUP || !(allocation instanceof TypeInsnNode type) || type.getOpcode() != NEW) return false;
        var id = "bewitchment:" + field.toLowerCase(Locale.ROOT);
        method.instructions.insertBefore(call, new LdcInsnNode(id));
        method.instructions.insertBefore(call, new FieldInsnNode(GETSTATIC, WORLD_GENERATORS, field, "Lnet/minecraft/class_5321;"));
        method.instructions.insertBefore(call, new MethodInsnNode(INVOKESTATIC, HELPER, "saplingGenerator",
                "(Ljava/lang/String;Lnet/minecraft/class_5321;)L" + NEW_GENERATOR + ";", false));
        method.instructions.remove(call);
        method.instructions.remove(duplicate);
        method.instructions.remove(allocation);
        count(counts, "sapling_generators");
        return true;
    }

    private static boolean useCalls(ClassNode node, MethodNode method, MethodInsnNode call, Scan scan, Map<String, Integer> counts) {
        if (!call.name.equals(OLD_USE_NAME) || !call.desc.equals(OLD_USE) || method.name.equals(WITH_ITEM_NAME)) return false;
        if (call.getOpcode() == INVOKESPECIAL) {
            if (scan.useOverrides.contains(call.owner)) return false;

            method.instructions.insertBefore(call, new InsnNode(SWAP));
            method.instructions.insertBefore(call, new InsnNode(POP));
            call.name = NEW_USE_NAME;
            call.desc = NEW_USE;
            count(counts, "use_super_calls");
            return true;
        }
        if (call.getOpcode() == INVOKEVIRTUAL && call.owner.equals("net/minecraft/class_2248")) {
            method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, HELPER, "legacyUse",
                    "(" + BLOCK + STATE + WORLD + POS + PLAYER + HAND + HIT + ")" + ACTION, false));
            count(counts, "dispenser_use_calls");
            return true;
        }
        reject(node, method, "Unexpected use dispatch");
        return false;
    }

    private static boolean foods(ClassNode node, MethodNode method, MethodInsnNode call, Map<String, Integer> counts) {
        if (call.owner.equals(FOOD_BUILDER) && call.name.equals("method_19236")) {

            method.instructions.remove(call);
            count(counts, "meat_flags_removed");
            return true;
        }
        if (call.owner.equals("net/minecraft/class_1792") && call.name.equals("method_19264")) {
            method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, HELPER, "food", "(" + ITEM + ")L" + FOOD + ";", false));
            count(counts, "item_food_lookups");
            return true;
        }
        if (!call.owner.equals(FOOD)) return false;
        switch (call.name) {
            case "method_19230" -> {
                call.name = "comp_2491";
                count(counts, "nutrition_reads");
            }
            case "method_19231" -> {
                method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, HELPER, "saturationModifier", "(L" + FOOD + ";)F", false));
                count(counts, "saturation_modifier_reads");
            }
            case "method_19232" -> {

                if (!(previous(call) instanceof VarInsnNode food) || food.getOpcode() != ALOAD
                        || !method.desc.startsWith("(" + WORLD + STACK)) reject(node, method, "Unexpected meat query");
                ((VarInsnNode) previous(call)).var = 2;
                method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, HELPER, "isMeat", "(" + STACK + ")Z", false));
                count(counts, "meat_tag_queries");
            }
            default -> { return false; }
        }
        return true;
    }

    private static boolean misc(ClassNode node, MethodNode method, MethodInsnNode call, Map<String, Integer> counts) {
        if (call.getOpcode() == INVOKESTATIC && FEATURE_KEYS.containsKey(call.owner + "." + call.name)) {

            method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, HELPER, FEATURE_KEYS.get(call.owner + "." + call.name),
                    "(Ljava/lang/String;)Lnet/minecraft/class_5321;", false));
            count(counts, "feature_keys");
            return true;
        }
        if (call.name.equals("method_5566") && call.desc.equals(EFFECT_MODIFIER)) {

            method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, HELPER, "effectModifier",
                    "(" + EFFECT + EFFECT_MODIFIER.substring(1), false));
            count(counts, "effect_attribute_modifiers");
            return true;
        }
        if (call.owner.equals(POINTER) && POINTERS.containsKey(call.name)) {
            var target = POINTERS.get(call.name).split(":");
            method.instructions.set(call, new MethodInsnNode(INVOKEVIRTUAL, POINTER, target[0], target[1], false));
            count(counts, "block_pointer_reads");
            return true;
        }
        if (call.owner.equals(ACCESSOR) && call.name.equals("callCreateLeavesBlock")) {

            method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, "net/minecraft/class_2246", "method_26106",
                    "(Lnet/minecraft/class_2498;)" + BLOCK, false));
            count(counts, "leaf_factories");
            return true;
        }
        if (call.owner.equals(SETTINGS_BUILDER) && call.name.equals("drops") && call.desc.equals("(Lnet/minecraft/class_2960;)L" + SETTINGS_BUILDER + ";")) {
            method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, HELPER, "drops",
                    "(L" + SETTINGS_BUILDER + ";Lnet/minecraft/class_2960;)L" + SETTINGS_BUILDER + ";", false));
            count(counts, "loot_table_keys");
            return true;
        }
        if (call.getOpcode() == INVOKESPECIAL && call.name.equals(PICK_NAME) && call.desc.equals(PICK_OLD)) {
            call.desc = PICK_NEW;
            count(counts, "pick_stack_super_calls");
            return true;
        }
        return false;
    }

    private static void addBridge(ClassNode node, Map<String, Integer> counts) {
        var method = new MethodNode(ACC_PUBLIC, WITH_ITEM_NAME, WITH_ITEM, null, null);
        var code = method.instructions;
        code.add(new VarInsnNode(ALOAD, 0));
        for (int slot = 2; slot <= 7; slot++) code.add(new VarInsnNode(ALOAD, slot));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, node.name, OLD_USE_NAME, OLD_USE, false));
        code.add(new MethodInsnNode(INVOKESTATIC, HELPER, "itemResult", "(" + ACTION + ")" + ITEM_ACTION, false));
        code.add(new InsnNode(ARETURN));
        method.maxStack = 7;
        method.maxLocals = 8;
        node.methods.add(method);
        count(counts, "use_with_item_bridges");
    }

    private static void addEffectBridge(ClassNode node, Map<String, Integer> counts) {
        var method = new MethodNode(ACC_PUBLIC, EFFECT_TICK, EFFECT_TICK_NEW, null, null);
        var code = method.instructions;
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new VarInsnNode(ALOAD, 1));
        code.add(new VarInsnNode(ILOAD, 2));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, node.name, EFFECT_TICK, EFFECT_TICK_OLD, false));

        code.add(new InsnNode(ICONST_1));
        code.add(new InsnNode(IRETURN));
        method.maxStack = 3;
        method.maxLocals = 3;
        node.methods.add(method);
        count(counts, "effect_tick_bridges");
    }

    private static void addCodec(ClassNode node, Map<String, Integer> counts) {
        var method = new MethodNode(ACC_PROTECTED, CODEC_NAME, CODEC_DESC, null, null);
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new MethodInsnNode(INVOKESTATIC, HELPER, "fixedCodec", "(" + BLOCK + ")Lcom/mojang/serialization/MapCodec;", false));
        method.instructions.add(new InsnNode(ARETURN));
        method.maxStack = 1;
        method.maxLocals = 1;
        node.methods.add(method);
        count(counts, "block_codecs");
    }

    private static void attributes(ClassNode node, MethodNode method, Map<String, Integer> counts) {
        for (var insn : method.instructions.toArray()) {
            if (!(insn instanceof FieldInsnNode field) || field.getOpcode() != GETSTATIC) continue;
            var replacement = LIBRARY_ATTRIBUTES.get(field.owner + "." + field.name);
            if (replacement == null) continue;
            if (field.name.equals("STEP_HEIGHT") && node.name.equals(WEREWOLF)) {

                var next = field.getNext();
                while (next.getOpcode() < 0) next = next.getNext();
                if (next.getOpcode() != DCONST_1) reject(node, method, "Unexpected step height");
                method.instructions.set(next, new LdcInsnNode(1.6D));
                count(counts, "absolute_step_defaults");
            }
            method.instructions.set(field, new MethodInsnNode(INVOKESTATIC, HELPER, replacement, "()Lnet/minecraft/class_1320;", false));
            count(counts, "library_attribute_reads");
        }
    }

    private static void worldGenerators(ClassNode node, Map<String, Integer> counts) {
        for (var method : node.methods) for (var insn : method.instructions.toArray()) {
            if (insn instanceof FieldInsnNode field && field.owner.equals(OLD_SPAWN_LOCATION)) {
                method.instructions.set(field, new FieldInsnNode(GETSTATIC, "net/minecraft/class_9169", "field_48745", "Lnet/minecraft/class_9168;"));
                count(counts, "spawn_locations");
            } else if (insn instanceof MethodInsnNode call && call.owner.equals("net/minecraft/class_1317") && call.name.equals("method_20637")
                    && call.desc.contains(OLD_SPAWN_LOCATION)) {
                call.desc = call.desc.replace("L" + OLD_SPAWN_LOCATION + ";", "Lnet/minecraft/class_9168;");
                count(counts, "spawn_restrictions");
            } else if (insn instanceof FieldInsnNode field && field.name.equals("MODIFY") && field.owner.equals(LOOT_EVENTS)) {
                var dynamic = field.getNext();
                while (dynamic.getOpcode() < 0) dynamic = dynamic.getNext();
                var register = dynamic.getNext();
                while (register.getOpcode() < 0) register = register.getNext();
                if (!(dynamic instanceof InvokeDynamicInsnNode) || !(register instanceof MethodInsnNode call) || !call.name.equals("register"))
                    reject(node, method, "Unexpected loot registration");
                method.instructions.remove(dynamic);
                method.instructions.remove(field);
                method.instructions.set(register, new MethodInsnNode(INVOKESTATIC, HELPER, "registerLootInjections", "()V", false));
                count(counts, "loot_registrations");
            }
        }

        if (node.methods.removeIf(m -> m.desc.contains("Lnet/minecraft/class_60;"))) count(counts, "obsolete_loot_lambdas");
    }

    private static void reserveEffects(ClassNode node, Map<String, Integer> counts) {
        for (var method : node.methods) for (var insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode call && call.owner.equals("net/minecraft/class_2378") && call.name.equals("method_10230")) {
                call.owner = HELPER;
                call.itf = false;
                call.name = "registerOnce";
                count(counts, "effect_registrations_deferred");
            } else if (method.name.equals("create") && insn.getOpcode() == ARETURN
                    && !(previous(insn) instanceof MethodInsnNode call && call.name.equals("reserveEffect"))
                    && !(previous(previous(insn)) instanceof MethodInsnNode prior && prior.name.equals("reserveEffect"))) {
                var code = new InsnList();
                code.add(new VarInsnNode(ALOAD, 1));
                code.add(new VarInsnNode(ALOAD, 0));
                code.add(new MethodInsnNode(INVOKESTATIC, P + "common/Bewitchment", "id", "(Ljava/lang/String;)Lnet/minecraft/class_2960;", false));
                code.add(new MethodInsnNode(INVOKESTATIC, HELPER, "reserveEffect", "(" + EFFECT + "Lnet/minecraft/class_2960;)V", false));
                method.instructions.insertBefore(insn, code);
                count(counts, "effect_reservations");
            }
        }
    }

    private static boolean groups(MethodNode method, MethodInsnNode call, Map<String, Integer> counts) {
        if (call.getOpcode() != INVOKEVIRTUAL || !call.name.equals(GROUP_GETTER) || !call.desc.equals("()L" + OLD_GROUP + ";")) return false;
        method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, GROUP, "of", "(Lnet/minecraft/class_1309;)L" + GROUP + ";", false));
        count(counts, "entity_group_queries");
        return true;
    }

    private static ClassNode relocateGroup(ClassNode node, Map<String, Integer> counts) {
        if (!node.name.startsWith(P) || node.name.contains("/mixin/")) return node;
        var before = new org.objectweb.asm.ClassWriter(0);
        node.accept(before);
        if (!new String(before.toByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1).contains(OLD_GROUP)) return node;
        if (has(node, GROUP_GETTER, "()L" + OLD_GROUP + ";") && !node.interfaces.contains(GROUP + "$Provider")) {
            node.interfaces.add(GROUP + "$Provider");
            count(counts, "entity_group_providers");
        }
        var map = new TreeMap<String, String>();
        map.put(OLD_GROUP, GROUP);
        GROUP_FIELDS.forEach((field, name) -> map.put(OLD_GROUP + "." + field, name));
        var result = new ClassNode();
        node.accept(new ClassRemapper(result, new SimpleRemapper(map)));
        count(counts, "entity_group_classes");
        return result;
    }

    private static void repairFrames(MethodNode method, Map<String, Integer> counts) {
        for (var insn : method.instructions.toArray()) {
            if (!(insn instanceof FrameNode frame) || frame.stack == null) continue;
            var kept = new ArrayList<Object>();
            for (var value : frame.stack) {
                if (value instanceof LabelNode label) {
                    AbstractInsnNode target = label;
                    while (target != null && target.getOpcode() < 0) target = target.getNext();
                    if (target == null || target.getOpcode() != NEW) {
                        count(counts, "stale_uninitialized_frame_values");
                        continue;
                    }
                }
                kept.add(value);
            }
            if (kept.size() == frame.stack.size()) continue;
            frame.stack = kept;
            if (frame.type == F_SAME1 && kept.isEmpty()) frame.type = F_SAME;
        }
    }

    private static boolean has(ClassNode node, String name, String descriptor) {
        return node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor));
    }

    private static ClassNode relocate(ClassNode node, Map<String, Integer> counts) {
        var map = new TreeMap<String, String>();
        if (node.name.equals(SAPLING_BLOCK) || node.name.equals(OBJECTS)) map.put(OLD_GENERATOR, NEW_GENERATOR);
        if (node.name.equals(OBJECTS) || node.name.endsWith("/BewitchmentClient")) for (var name : List.of("block/TerraformHangingSignBlock",
                "block/TerraformSignBlock", "block/TerraformWallHangingSignBlock", "block/TerraformWallSignBlock", "SpriteIdentifierRegistry"))
            map.put(SIGN + name, SIGN + "api/" + name);
        if (map.isEmpty()) return node;
        var scan = new ClassNode();
        node.accept(scan);
        var before = new org.objectweb.asm.ClassWriter(0);
        scan.accept(before);
        var result = new ClassNode();
        node.accept(new ClassRemapper(result, new SimpleRemapper(map)));
        var after = new org.objectweb.asm.ClassWriter(0);
        result.accept(after);
        if (Arrays.equals(before.toByteArray(), after.toByteArray())) return node;
        if (node.name.equals(OBJECTS)) count(counts, "sign_library_relocations");
        if (node.name.equals(SAPLING_BLOCK)) {
            counts.merge("sapling_constructor_signatures", 2, Integer::sum);
        }
        return result;
    }

    public static Result rewrite(ClassNode node, Scan scan) {
        var counts = new TreeMap<String, Integer>();
        if (node.name.startsWith(P)) {
            int attributes = node.innerClasses.size();
            node.innerClasses.removeIf(inner -> REMOVED_INNER_CLASSES.contains(inner.name));
            if (node.innerClasses.size() != attributes) counts.merge("obsolete_inner_class_records", attributes - node.innerClasses.size(), Integer::sum);
            for (var method : node.methods) {
                for (var insn : method.instructions.toArray()) {
                    if (!(insn instanceof MethodInsnNode call)) continue;
                    if (saplings(method, call, counts) || constructors(node, method, call, counts)) continue;
                    if (projectiles(node, method, call, counts) || groups(method, call, counts)) continue;
                    if (useCalls(node, method, call, scan, counts) || foods(node, method, call, counts)) continue;
                    misc(node, method, call, counts);
                }
                attributes(node, method, counts);
                repairFrames(method, counts);
                if (method.name.equals(PICK_NAME) && method.desc.equals(PICK_OLD)) {
                    method.desc = PICK_NEW;
                    if (method.localVariables != null) for (var local : method.localVariables)
                        if (local.index == 1) local.desc = "Lnet/minecraft/class_4538;";
                    count(counts, "pick_stack_overrides");
                }
            }
            if (has(node, OLD_USE_NAME, OLD_USE) && !has(node, WITH_ITEM_NAME, WITH_ITEM)) addBridge(node, counts);
            if (CODEC_GAPS.contains(node.name) && !has(node, CODEC_NAME, CODEC_DESC)) addCodec(node, counts);
            if (node.name.equals(WORLD_GENERATORS)) worldGenerators(node, counts);
            if (node.name.equals(P + "common/registry/BWStatusEffects")) reserveEffects(node, counts);
            if (has(node, EFFECT_TICK, EFFECT_TICK_OLD) && !has(node, EFFECT_TICK, EFFECT_TICK_NEW)) addEffectBridge(node, counts);
            if (PROJECTILES.containsKey(node.name) && !has(node, DEFAULT_STACK, "()" + STACK)) addDefaultStack(node, PROJECTILES.get(node.name), counts);
            if (node.name.equals(ARROW_ITEM + "$1")) rebaseDispenser(node, counts);
            if (node.name.equals(ARROW_ITEM) && !has(node, ARROW_NAME, ARROW_NEW)) addArrowBridge(node, counts);
        }
        return new Result(relocateGroup(relocate(node, counts), counts), counts);
    }
}
