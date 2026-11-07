package com.veeda.rewitchment.translator;

import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class ItemRuntimeAdapter {
    public static final String PREFIX = "moriyashiine/bewitchment/";
    public static final String HELPER = "com/veeda/rewitchment/probe/LegacyItemRuntime";
    public static final String STACK = "net/minecraft/class_1799";
    public static final String LIVING = "net/minecraft/class_1309";
    public static final String CONTEXT = "net/minecraft/class_1792$class_9635";
    public static final String USE_OLD = "(L" + STACK + ";)I";
    public static final String USE_NEW = "(L" + STACK + ";L" + LIVING + ";)I";
    public static final String TIP_OLD = "(L" + STACK + ";Lnet/minecraft/class_1937;Ljava/util/List;Lnet/minecraft/class_1836;)V";
    public static final String BLOCK_TIP_OLD = TIP_OLD.replace("class_1937", "class_1922");
    public static final String TIP_NEW = "(L" + STACK + ";L" + CONTEXT + ";Ljava/util/List;Lnet/minecraft/class_1836;)V";
    public static final Map<String,Integer> EXPECTED_COUNTS = Map.ofEntries(
            Map.entry("use_time_overrides",6), Map.entry("use_time_calls",2),
            Map.entry("tooltip_overrides",11), Map.entry("tooltip_forward_calls",1),
            Map.entry("potion_tooltips",3), Map.entry("entity_durability_calls",9),
            Map.entry("break_animation_calls",9), Map.entry("attribute_registry_queries",17),
            Map.entry("automatic_vertex_finalization",8), Map.entry("client_status_queries",4),
            Map.entry("mining_speed_dispatch",1), Map.entry("mining_suitability_dispatch",1));
    public static final Set<String> USE = names("common/item/", "BottleOfBloodItem", "CaduceusItem", "ContractItem", "CursePoppetItem", "HornedSpearItem", "ScepterItem");
    public static final Set<String> TIPS = new HashSet<>();
    public static final Set<String> DAMAGE = new HashSet<>();
    private static final Set<String> ATTR = new HashSet<>();
    static {
        TIPS.addAll(names("api/item/", "PoppetItem", "SigilItem"));
        TIPS.addAll(names("common/item/", "ChalkItem", "ContractItem", "CursePoppetItem", "DragonsBloodBroomItem", "PricklyBeltItem", "ScepterItem", "TaglockItem", "WaystoneItem"));
        TIPS.add(PREFIX + "common/block/BrazierBlock");
        DAMAGE.addAll(names("common/item/", "AthameItem", "CaduceusItem", "ChalkItem", "HornedSpearItem", "ScepterItem"));
        DAMAGE.addAll(names("common/block/entity/", "BrazierBlockEntity", "GlyphBlockEntity"));
        DAMAGE.add(PREFIX + "common/block/dragonsblood/DragonsBloodLogBlock");
        DAMAGE.add(PREFIX + "api/block/CandelabraBlock");
        ATTR.addAll(names("common/entity/living/", "OwlEntity", "RavenEntity", "SnakeEntity", "ToadEntity"));
        ATTR.add(PREFIX + "api/component/TransformationComponent");
        ATTR.add(PREFIX + "mixin/familiar/PlayerEntityMixin");
    }
    private static Set<String> names(String path, String... names) {
        var result = new HashSet<String>();
        for (String name : names) result.add(PREFIX + path + name);
        return result;
    }
    public static boolean expandedFrames(String owner) { return USE.contains(owner) || TIPS.contains(owner); }
    public static final class Stats {
        public final Map<String, Integer> counts = new TreeMap<>();
        void add(String key) { counts.merge(key, 1, Integer::sum); }
        public int total() { return counts.values().stream().mapToInt(Integer::intValue).sum(); }
    }
    private ItemRuntimeAdapter() {}
    private static AbstractInsnNode next(AbstractInsnNode i) {
        i = i.getNext();
        while (i != null && i.getOpcode() < 0) i = i.getNext();
        return i;
    }
    private static boolean load(AbstractInsnNode i, int local) {
        return i instanceof VarInsnNode v && v.getOpcode() == Opcodes.ALOAD && v.var == local;
    }
    private static void addedArgument(MethodNode method, int slot, String owner) {
        for (var i : method.instructions.toArray()) {
            if (i instanceof VarInsnNode v && v.var >= slot) v.var++;
            else if (i instanceof IincInsnNode v && v.var >= slot) v.var++;
            else if (i instanceof FrameNode f) {
                if (f.type != Opcodes.F_NEW || f.local == null || f.local.size() < slot)
                    throw new IllegalStateException("Expanded frames required: " + owner);
                f.local.add(slot, LIVING);
            }
        }
        if (method.localVariables != null) method.localVariables.forEach(v -> { if (v.index >= slot) v.index++; });
        if (method.visibleLocalVariableAnnotations != null) method.visibleLocalVariableAnnotations.forEach(a -> a.index.replaceAll(i -> i >= slot ? i + 1 : i));
        if (method.invisibleLocalVariableAnnotations != null) method.invisibleLocalVariableAnnotations.forEach(a -> a.index.replaceAll(i -> i >= slot ? i + 1 : i));
        method.maxLocals++;
        method.maxStack++;
        method.signature = null;
    }
    public static Stats rewrite(ClassNode node) {
        Stats stats = new Stats();
        if (!node.name.startsWith(PREFIX)) return stats;
        for (var method : node.methods) {
            boolean use = USE.contains(node.name) && method.name.equals("method_7881") && method.desc.equals(USE_OLD);
            boolean tip = TIPS.contains(node.name) && ((method.name.equals("method_7851") && method.desc.equals(TIP_OLD))
                    || (method.name.equals("method_9568") && method.desc.equals(BLOCK_TIP_OLD)));
            if (use) {
                if ((method.access & Opcodes.ACC_STATIC) != 0) throw new IllegalStateException("Static item override");
                addedArgument(method, 2, node.name);
                method.desc = USE_NEW;
                stats.add("use_time_overrides");
            }
            if (tip) {

                for (var i : method.instructions.toArray()) {
                    if (i instanceof VarInsnNode v && v.var == 2) {
                        var a = next(i); var b = a == null ? null : next(a); var c = b == null ? null : next(b);
                        if (!load(i, 2) || !load(a, 3) || !load(b, 4) || !(c instanceof MethodInsnNode call)
                                || !(call.name.equals("method_7851") || call.name.equals("method_9568"))
                                || !(call.desc.equals(TIP_OLD) || call.desc.equals(BLOCK_TIP_OLD)))
                            throw new IllegalStateException("Unsupported tooltip world use: " + node.name);
                    }
                    if (i instanceof FrameNode f && f.local != null && f.local.size() > 2
                            && ("net/minecraft/class_1937".equals(f.local.get(2)) || "net/minecraft/class_1922".equals(f.local.get(2))))
                        f.local.set(2, CONTEXT);
                }
                if (method.localVariables != null) method.localVariables.forEach(v -> {
                    if (v.index == 2) { v.desc = "L" + CONTEXT + ";"; v.signature = null; }
                });
                method.desc = TIP_NEW;
                method.signature = null;
                stats.add("tooltip_overrides");
            }
            if (node.name.equals(ToolAndWeaponAdapter.CADUCEUS)) {
                if (method.name.equals("method_7865") && method.desc.equals("(L" + STACK + ";Lnet/minecraft/class_2680;)F")) {
                    method.name = "method_58404";
                    stats.add("mining_speed_dispatch");
                } else if (method.name.equals("method_7856") && method.desc.equals("(Lnet/minecraft/class_2680;)Z")) {
                    var instructions = Arrays.stream(method.instructions.toArray()).filter(i -> i.getOpcode() >= 0).toList();
                    if (instructions.size() != 2 || instructions.get(0).getOpcode() != Opcodes.ICONST_1
                            || instructions.get(1).getOpcode() != Opcodes.IRETURN)
                        throw new IllegalStateException("Unexpected Caduceus suitability body");
                    method.name = "method_58405";
                    method.desc = "(L" + STACK + ";Lnet/minecraft/class_2680;)Z";
                    if (method.localVariables != null) method.localVariables.forEach(v -> { if (v.index == 1) v.index = 2; });
                    method.maxLocals = Math.max(method.maxLocals, 3);
                    stats.add("mining_suitability_dispatch");
                }
            }
            for (var i : method.instructions.toArray()) {
                if (!(i instanceof MethodInsnNode c)) continue;
                if (c.getOpcode() == Opcodes.INVOKEVIRTUAL && c.name.equals("method_7881") && c.desc.equals(USE_OLD)
                        && (use || node.name.equals(ToolAndWeaponAdapter.SPEAR) && method.name.equals("method_7840"))) {
                    method.instructions.insertBefore(c, new VarInsnNode(Opcodes.ALOAD, use ? 2 : 3));
                    c.desc = USE_NEW;
                    method.maxStack++;
                    stats.add("use_time_calls");
                } else if (tip && (c.name.equals("method_7851") || c.name.equals("method_9568"))
                        && (c.desc.equals(TIP_OLD) || c.desc.equals(BLOCK_TIP_OLD))) {
                    c.desc = TIP_NEW;
                    stats.add("tooltip_forward_calls");
                } else if (tip && c.getOpcode() == Opcodes.INVOKESTATIC && c.owner.equals("net/minecraft/class_1844")
                        && c.name.equals("method_47372") && c.desc.equals("(L" + STACK + ";Ljava/util/List;F)V")) {
                    method.instructions.insertBefore(c, new VarInsnNode(Opcodes.ALOAD, 2));
                    c.owner = HELPER; c.name = "potionTooltip";
                    c.desc = "(L" + STACK + ";Ljava/util/List;FL" + CONTEXT + ";)V";
                    method.maxStack++;
                    stats.add("potion_tooltips");
                } else if (DAMAGE.contains(node.name) && c.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && c.owner.equals(STACK) && c.name.equals("method_7956")
                        && c.desc.equals("(IL" + LIVING + ";Ljava/util/function/Consumer;)V")) {
                    c.setOpcode(Opcodes.INVOKESTATIC); c.owner = HELPER; c.name = "damage";
                    c.desc = "(L" + STACK + ";IL" + LIVING + ";Ljava/util/function/Consumer;)V"; c.itf = false;
                    stats.add("entity_durability_calls");
                } else if (DAMAGE.contains(node.name) && method.name.startsWith("lambda$")
                        && c.getOpcode() == Opcodes.INVOKEVIRTUAL && (c.owner.equals(LIVING) || c.owner.equals("net/minecraft/class_1657"))
                        && c.name.equals("method_20236") && c.desc.equals("(Lnet/minecraft/class_1268;)V")) {
                    c.setOpcode(Opcodes.INVOKESTATIC); c.owner = HELPER; c.name = "sendToolBreakStatus";
                    c.desc = "(L" + LIVING + ";Lnet/minecraft/class_1268;)V"; c.itf = false;
                    stats.add("break_animation_calls");
                } else if (ATTR.contains(node.name) && c.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && (c.owner.equals(node.name) || c.owner.equals("net/minecraft/class_1657"))
                        && c.name.equals("method_5996") && c.desc.equals("(Lnet/minecraft/class_1320;)Lnet/minecraft/class_1324;")) {
                    c.setOpcode(Opcodes.INVOKESTATIC); c.owner = HELPER; c.name = "attribute";
                    c.desc = "(L" + LIVING + ";Lnet/minecraft/class_1320;)Lnet/minecraft/class_1324;"; c.itf = false;
                    stats.add("attribute_registry_queries");
                } else if ((node.name.startsWith(PREFIX + "client/") || node.name.equals(PREFIX + "mixin/client/ClientWorldMixin"))
                        && c.owner.equals("net/minecraft/class_746")
                        && c.getOpcode() == Opcodes.INVOKEVIRTUAL && (c.name.equals("method_6059") || c.name.equals("method_6112"))
                        && (c.desc.equals("(Lnet/minecraft/class_1291;)Z") || c.desc.equals("(Lnet/minecraft/class_1291;)Lnet/minecraft/class_1293;"))) {
                    c.setOpcode(Opcodes.INVOKESTATIC); c.owner = "com/veeda/rewitchment/probe/LegacyStatusEffects";
                    c.name = c.name.equals("method_6059") ? "hasStatusEffect" : "getStatusEffect";
                    c.desc = "(L" + LIVING + ";" + c.desc.substring(1); c.itf = false;
                    stats.add("client_status_queries");
                } else if (node.name.equals(PREFIX + "client/render/blockentity/WitchCauldronBlockEntityRenderer")
                        && c.owner.equals("net/minecraft/class_4588") && c.getOpcode() == Opcodes.INVOKEINTERFACE
                        && c.name.equals("method_1344") && c.desc.equals("()V")) {
                    var before = c.getPrevious();
                    while (before != null && before.getOpcode() < 0) before = before.getPrevious();
                    if (!(before instanceof MethodInsnNode previous) || !previous.owner.equals(c.owner)
                            || !previous.desc.endsWith("Lnet/minecraft/class_4588;"))
                        throw new IllegalStateException("Unexpected vertex finalization chain");

                    method.instructions.set(c, new InsnNode(Opcodes.POP));
                    stats.add("automatic_vertex_finalization");
                }
            }
        }
        return stats;
    }
}
