package com.veeda.rewitchment.translator;

import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

public final class LegacyMethodAdapter {
    private static final String EFFECT = "net/minecraft/class_1291";
    private static final String EFFECT_INSTANCE = "net/minecraft/class_1293";
    private static final String ENTITY = "net/minecraft/class_1309";
    private static final String ATTR = "net/minecraft/class_1320";
    private static final String BUILDER = "net/minecraft/class_5132$class_5133";
    private static final String RENDER_MODEL = "net/minecraft/class_3879";
    private static final String RENDER_PART = "net/minecraft/class_630";
    private static final String MATRIX = "net/minecraft/class_4587";
    private static final String VERTEX = "net/minecraft/class_4588";
    private static final String EFFECT_BRIDGE = "com/veeda/rewitchment/probe/LegacyStatusEffects";
    private static final String ATTR_BRIDGE = "com/veeda/rewitchment/probe/LegacyEntityAttributes";
    private static final String MODEL_BRIDGE = "com/veeda/rewitchment/probe/LegacyModelRendering";
    private static final String ITEM_BRIDGE = "com/veeda/rewitchment/probe/LegacyItemNames";
    private static final String ITEM_NBT_BRIDGE = "com/veeda/rewitchment/probe/LegacyItemNbt";
    private static final String ITEM = "net/minecraft/class_1799";
    private static final String ARMOR_ITEM = "net/minecraft/class_1738";
    private static final String ARMOR_MATERIAL = "net/minecraft/class_1741";
    private static final String REGISTRY_ENTRY = "net/minecraft/class_6880";

    private static final Set<String> EFFECT_CALL_OWNERS = Set.of(
            "net/minecraft/class_1309",  
            "net/minecraft/class_1657",  
            "net/minecraft/class_3222"   
    );

    private static final Set<String> FIRE_CALL_OWNERS = Set.of(
            "net/minecraft/class_1297",  
            "net/minecraft/class_1309",
            "net/minecraft/class_1657",
            "net/minecraft/class_1308"
    );

    private static final Map<String, String> EFFECT_METHODS = Map.of(
            "method_6059(L" + EFFECT + ";)Z", "hasStatusEffect",
            "method_6112(L" + EFFECT + ";)L" + EFFECT_INSTANCE + ";", "getStatusEffect",
            "method_6016(L" + EFFECT + ";)Z", "removeStatusEffect"
    );

    private static final String RENDER_OLD =
            "(L" + MATRIX + ";L" + VERTEX + ";IIFFFF)V";
    private static final String RENDER_NEW_PREFIX = "(L";

    public record Outcome(int statusEffects, int attributes, int modelParts,
                          int models, int fireConversions, int customNames,
                          int hasLegacyNbtChecks, int armorMaterials) {}

    private LegacyMethodAdapter() {}

    public static Outcome rewrite(ClassNode node) {
        int effects = 0, attributes = 0, parts = 0, models = 0, fire = 0, names = 0, hasNbt = 0, armor = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;) {
                AbstractInsnNode next = insn.getNext();
                if (!(insn instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKEVIRTUAL) {
                    insn = next;
                    continue;
                }

                if (EFFECT_CALL_OWNERS.contains(call.owner)) {
                    String bridgeMethod = EFFECT_METHODS.get(call.name + call.desc);
                    if (bridgeMethod != null) {
                        String descriptor = "(L" + ENTITY + ";" + call.desc.substring(1);
                        method.instructions.set(call, new MethodInsnNode(
                                Opcodes.INVOKESTATIC, EFFECT_BRIDGE, bridgeMethod,
                                descriptor, false));
                        effects++;
                        insn = next;
                        continue;
                    }
                }

                if (BUILDER.equals(call.owner) && "method_26868".equals(call.name)
                        && call.desc.equals("(L" + ATTR + ";D)L" + BUILDER + ";")) {
                    String descriptor = "(L" + BUILDER + ";L" + ATTR + ";D)L" + BUILDER + ";";
                    method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                            ATTR_BRIDGE, "add", descriptor, false));
                    attributes++;
                    insn = next;
                    continue;
                }

                if (ITEM.equals(call.owner)) {
                    if ("method_7985".equals(call.name) && "()Z".equals(call.desc)) {
                        method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                ITEM_NBT_BRIDGE, "hasLegacyNbt",
                                "(L" + ITEM + ";)Z", false));
                        hasNbt++;
                        insn = next;
                        continue;
                    }
                    if ("method_7977".equals(call.name)
                            && "(Lnet/minecraft/class_2561;)Lnet/minecraft/class_1799;".equals(call.desc)) {
                        method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                ITEM_BRIDGE, "setCustomName",
                                "(L" + ITEM + ";Lnet/minecraft/class_2561;)L" + ITEM + ";", false));
                        names++;
                        insn = next;
                        continue;
                    }
                    if ("method_7938".equals(call.name) && "()Z".equals(call.desc)) {
                        method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                ITEM_BRIDGE, "hasCustomName",
                                "(L" + ITEM + ";)Z", false));
                        names++;
                        insn = next;
                        continue;
                    }
                }

                if (ARMOR_ITEM.equals(call.owner) && "method_7686".equals(call.name)
                        && ("()L" + ARMOR_MATERIAL + ";").equals(call.desc)) {

                    call.desc = "()L" + REGISTRY_ENTRY + ";";
                    MethodInsnNode value = new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                            REGISTRY_ENTRY, "comp_349", "()Ljava/lang/Object;", true);
                    method.instructions.insert(call, value);
                    method.instructions.insert(value, new TypeInsnNode(
                            Opcodes.CHECKCAST, ARMOR_MATERIAL));
                    armor++;
                    insn = next;
                    continue;
                }

                if ((RENDER_PART.equals(call.owner) || RENDER_MODEL.equals(call.owner))
                        && RENDER_OLD.equals(call.desc)
                        && (RENDER_PART.equals(call.owner) && "method_22699".equals(call.name)
                            || RENDER_MODEL.equals(call.owner) && "method_2828".equals(call.name))) {
                    String desc = "(L" + call.owner + ";" + call.desc.substring(1);
                    method.instructions.set(call, new MethodInsnNode(
                            Opcodes.INVOKESTATIC, MODEL_BRIDGE, "render", desc, false));
                    if (RENDER_PART.equals(call.owner)) parts++;
                    else models++;
                    insn = next;
                    continue;
                }

                if (FIRE_CALL_OWNERS.contains(call.owner)
                        && "method_5639".equals(call.name)
                        && "(I)V".equals(call.desc)) {

                    method.instructions.insertBefore(call, new InsnNode(Opcodes.I2F));
                    call.desc = "(F)V";
                    fire++;
                }
                insn = next;
            }
        }
        return new Outcome(effects, attributes, parts, models, fire, names, hasNbt, armor);
    }
}
