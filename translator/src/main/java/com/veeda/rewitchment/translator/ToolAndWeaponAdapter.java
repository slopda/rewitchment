package com.veeda.rewitchment.translator;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class ToolAndWeaponAdapter {
    public static final String PREFIX = "moriyashiine/bewitchment/";
    public static final String MATERIALS = PREFIX + "common/registry/BWMaterials";
    public static final String OBJECTS = PREFIX + "common/registry/BWObjects";
    public static final String SILVER_CLASS = MATERIALS + "$5";
    public static final String ATHAME = PREFIX + "common/item/AthameItem";
    public static final String SPEAR = PREFIX + "common/item/HornedSpearItem";
    public static final String CADUCEUS = PREFIX + "common/item/CaduceusItem";
    public static final String HELPER = "com/veeda/rewitchment/probe/LegacyWeaponComponents";
    public static final String TOOL = "net/minecraft/class_1832";
    public static final String SETTINGS = "net/minecraft/class_1792$class_1793";
    public static final String MODIFIER = "net/minecraft/class_1322";
    public static final String SWORD = "net/minecraft/class_1829";
    public static final String MINING = "net/minecraft/class_1766";
    public static final String TAG = "net/minecraft/class_6862";
    public static final String SWORD_OLD = "(L" + TOOL + ";IFL" + SETTINGS + ";)V";
    public static final String SWORD_NEW = "(L" + TOOL + ";L" + SETTINGS + ";)V";
    public static final String MINING_OLD = "(FFL" + TOOL + ";L" + TAG + ";L" + SETTINGS + ";)V";
    public static final String MINING_NEW = "(L" + TOOL + ";L" + TAG + ";L" + SETTINGS + ";)V";
    public static final String ATTR_DESC = "(Lnet/minecraft/class_1304;)Lcom/google/common/collect/Multimap;";
    private static final Set<String> SWORDS = Set.of(ATHAME, SPEAR);

    public record Outcome(int silverFactories, int swordConstructors, int miningConstructors,
                          int reachOverrides, int miningSpeedFields, int rejected,
                          int explicitDurabilities) {
        public Outcome(int silver, int swords, int mining, int reach, int speed, int rejected) {
            this(silver, swords, mining, reach, speed, rejected, 0);
        }
    }
    private ToolAndWeaponAdapter() {}

    private static List<AbstractInsnNode> real(MethodNode method) {
        return Arrays.stream(method.instructions.toArray()).filter(i -> i.getOpcode() >= 0).toList();
    }

    private static boolean loads(List<AbstractInsnNode> insns, int[] opcodes, int[] locals) {
        if (insns.size() <= opcodes.length) return false;
        for (int i = 0; i < opcodes.length; i++) {
            if (!(insns.get(i) instanceof VarInsnNode v) || v.getOpcode() != opcodes[i]
                    || v.var != locals[i]) return false;
        }
        return true;
    }

    private static boolean oldSuper(AbstractInsnNode insn, String owner, String desc) {
        return insn instanceof MethodInsnNode c && c.getOpcode() == Opcodes.INVOKESPECIAL
                && !c.itf && c.owner.equals(owner) && c.name.equals("<init>") && c.desc.equals(desc);
    }

    private static boolean reachOverride(MethodNode method, String owner) {
        if (!method.name.equals("method_7844") || !method.desc.equals(ATTR_DESC)) return false;
        var instructions = real(method);
        var fields = instructions.stream().filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast).toList();
        var calls = instructions.stream().filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        return fields.size() == 3 && calls.size() == 3
                && fields.stream().allMatch(f -> f.getOpcode() == Opcodes.GETSTATIC)
                && fields.stream().anyMatch(f -> f.owner.equals(owner) && f.name.equals("REACH_MODIFIER")
                    && f.desc.equals("L" + MODIFIER + ";"))
                && fields.stream().anyMatch(f -> f.owner.equals("com/jamieswhiteshirt/reachentityattributes/ReachEntityAttributes")
                    && f.name.equals("ATTACK_RANGE") && f.desc.equals("Lnet/minecraft/class_1320;"))
                && fields.stream().anyMatch(f -> f.owner.equals("net/minecraft/class_1304") && f.name.equals("field_6173"))
                && calls.stream().anyMatch(c -> c.owner.equals(SWORD) && c.name.equals("method_7844")
                    && c.desc.equals(ATTR_DESC) && c.getOpcode() == Opcodes.INVOKESPECIAL)
                && calls.stream().anyMatch(c -> c.owner.equals("com/google/common/collect/LinkedHashMultimap") && c.name.equals("create"))
                && calls.stream().anyMatch(c -> c.owner.equals("com/google/common/collect/Multimap") && c.name.equals("put"));
    }

    public static Outcome rewrite(ClassNode node) {
        if (!node.name.startsWith(PREFIX)) return new Outcome(0, 0, 0, 0, 0, 0);
        int silver = 0, swords = 0, mining = 0, overrides = 0, speedFields = 0, rejected = 0;
        int durability = 0;
        if (node.name.equals(MATERIALS)) {
            var result = IdentifierFactoryAdapter.rewrite(node, SILVER_CLASS, Map.of("()V",
                    new IdentifierFactoryAdapter.Factory(HELPER, "silver", "()L" + TOOL + ";")));
            silver = result.converted();
            rejected += result.rejected();
        }
        if (node.name.equals(OBJECTS)) {
            for (var method : node.methods) {
                if (!method.name.equals("<clinit>") || !method.desc.equals("()V")) continue;
                var insns = real(method);
                for (int end = 0; end < insns.size(); end++) {
                    if (!(insns.get(end) instanceof MethodInsnNode ctor)
                            || ctor.getOpcode() != Opcodes.INVOKESPECIAL || !ctor.name.equals("<init>")
                            || !(ctor.owner.equals(SPEAR) && ctor.desc.equals(SWORD_OLD)
                                || ctor.owner.equals(CADUCEUS) && ctor.desc.equals(MINING_OLD))) continue;
                    int start = end - 1;
                    while (start >= 0 && !(insns.get(start) instanceof TypeInsnNode t
                            && t.getOpcode() == Opcodes.NEW && t.desc.equals(ctor.owner))) start--;
                    if (start < 0) { rejected++; continue; }
                    var region = insns.subList(start, end);

                    if (region.stream().anyMatch(i -> i instanceof MethodInsnNode c
                            && c.owner.equals(HELPER) && c.name.equals("withDurability"))) continue;
                    var materials = region.stream().filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast)
                            .filter(f -> f.getOpcode() == Opcodes.GETSTATIC && f.owner.equals("net/minecraft/class_1834")
                                && f.name.equals("field_22033") && f.desc.equals("Lnet/minecraft/class_1834;")).toList();
                    var setters = region.stream().filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                            .filter(c -> c.owner.equals(SETTINGS) && c.name.equals("method_7895")
                                && c.desc.equals("(I)L" + SETTINGS + ";")).toList();
                    int expected = ctor.owner.equals(SPEAR) ? 1008 : 1998;
                    if (materials.size() != 1 || setters.size() != 1) { rejected++; continue; }
                    int index = insns.indexOf(setters.getFirst());
                    if (!(insns.get(index - 1) instanceof IntInsnNode value)
                            || value.operand != expected || value.getOpcode() != Opcodes.SIPUSH) { rejected++; continue; }
                    InsnList wrapper = new InsnList();
                    wrapper.add(new IntInsnNode(Opcodes.SIPUSH, value.operand));
                    wrapper.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "withDurability",
                            "(L" + TOOL + ";I)L" + TOOL + ";", false));
                    method.instructions.insert(materials.getFirst(), wrapper);
                    method.maxStack += 1;
                    durability++;
                }
            }
        }
        if (SWORDS.contains(node.name)) {
            var old = node.methods.stream().filter(m -> m.name.equals("method_7844") && m.desc.equals(ATTR_DESC)).toList();
            var constructors = node.methods.stream().filter(m -> m.name.equals("<init>") && m.desc.equals(SWORD_OLD)).toList();
            boolean pending = constructors.stream().anyMatch(m -> real(m).stream().anyMatch(i -> oldSuper(i, SWORD, SWORD_OLD)));
            if (pending) {
                if (!node.superName.equals(SWORD) || old.size() != 1 || !reachOverride(old.getFirst(), node.name)
                        || constructors.size() != 1) return new Outcome(0, 0, 0, 0, 0, 1);
                var method = constructors.getFirst();
                var insns = real(method);
                if (!loads(insns, new int[]{Opcodes.ALOAD,Opcodes.ALOAD,Opcodes.ILOAD,Opcodes.FLOAD,Opcodes.ALOAD}, new int[]{0,1,2,3,4})
                        || !oldSuper(insns.get(5), SWORD, SWORD_OLD)) return new Outcome(0, 0, 0, 0, 0, 1);

                method.instructions.insert(insns.get(1), new InsnNode(Opcodes.DUP));
                InsnList extra = new InsnList();
                extra.add(new FieldInsnNode(Opcodes.GETSTATIC, node.name, "REACH_MODIFIER", "L" + MODIFIER + ";"));
                extra.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "swordSettings",
                        "(L" + TOOL + ";IFL" + SETTINGS + ";L" + MODIFIER + ";)L" + SETTINGS + ";", false));
                method.instructions.insertBefore(insns.get(5), extra);
                ((MethodInsnNode) insns.get(5)).desc = SWORD_NEW;
                method.maxStack = Math.max(method.maxStack + 2, 8);
                node.methods.remove(old.getFirst());
                swords++;
                overrides++;
            }
        } else if (node.name.equals(CADUCEUS)) {
            for (MethodNode method : node.methods) {
                if (method.name.equals("<init>") && method.desc.equals(MINING_OLD)) {
                    var insns = real(method);
                    if (insns.stream().anyMatch(i -> oldSuper(i, MINING, MINING_OLD))) {
                        if (!node.superName.equals(MINING)
                                || !loads(insns, new int[]{Opcodes.ALOAD,Opcodes.FLOAD,Opcodes.FLOAD,Opcodes.ALOAD,Opcodes.ALOAD,Opcodes.ALOAD}, new int[]{0,1,2,3,4,5})
                                || !oldSuper(insns.get(6), MINING, MINING_OLD)) {
                            rejected++;
                            continue;
                        }
                        InsnList prefix = new InsnList();
                        prefix.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        prefix.add(new VarInsnNode(Opcodes.ALOAD, 3));
                        prefix.add(new VarInsnNode(Opcodes.ALOAD, 4));
                        prefix.add(new VarInsnNode(Opcodes.ALOAD, 3));
                        prefix.add(new VarInsnNode(Opcodes.FLOAD, 1));
                        prefix.add(new VarInsnNode(Opcodes.FLOAD, 2));
                        prefix.add(new VarInsnNode(Opcodes.ALOAD, 5));
                        prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "miningSettings",
                                "(L" + TOOL + ";FFL" + SETTINGS + ";)L" + SETTINGS + ";", false));
                        method.instructions.insertBefore(insns.getFirst(), prefix);
                        for (int i = 0; i < 6; i++) method.instructions.remove(insns.get(i));
                        ((MethodInsnNode) insns.get(6)).desc = MINING_NEW;
                        method.maxStack = Math.max(method.maxStack, 8);
                        mining++;
                    }
                }
                if (method.name.equals("method_7865")
                        && method.desc.equals("(Lnet/minecraft/class_1799;Lnet/minecraft/class_2680;)F")) {
                    for (var insn : method.instructions.toArray()) {
                        if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                                && field.owner.equals(CADUCEUS) && field.name.equals("field_7940") && field.desc.equals("F")) {
                            method.instructions.set(field, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER,
                                    "miningSpeed", "(Lnet/minecraft/class_1831;)F", false));
                            speedFields++;
                        }
                    }
                }
            }
        }
        if (node.nestMembers != null) node.nestMembers.removeIf(SILVER_CLASS::equals);
        if (node.innerClasses != null) node.innerClasses.removeIf(c -> SILVER_CLASS.equals(c.name));
        return new Outcome(silver, swords, mining, overrides, speedFields, rejected, durability);
    }
}
