package com.veeda.rewitchment.translator;

import java.util.Map;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class LegacyPotionAdapter {
    static final String POTION_UTIL = "net/minecraft/class_1844";
    static final String SHIM = "com/veeda/rewitchment/probe/LegacyPotionContents";
    private static final String STACK = "Lnet/minecraft/class_1799;";
    private static final String POTION = "Lnet/minecraft/class_1842;";
    private static final String LIST = "Ljava/util/List;";
    private static final String COLLECTION = "Ljava/util/Collection;";

    private static final Map<String, String> RULES = Map.of(
            "method_8068(" + STACK + ")" + LIST, "getCustomPotionEffects",
            "method_8067(" + STACK + ")" + LIST, "getPotionEffects",
            "method_8056(" + STACK + COLLECTION + ")" + STACK, "setCustomPotionEffects",
            "method_8055(" + COLLECTION + ")I", "getColor",
            "method_8061(" + STACK + POTION + ")" + STACK, "setPotion",
            "method_8063(" + STACK + ")" + POTION, "getPotion",
            "method_8064(" + STACK + ")I", "getColor"
    );

    private LegacyPotionAdapter() {}

    public static int rewrite(ClassNode node) {
        int changed = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst();
                 insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKESTATIC
                        && POTION_UTIL.equals(call.owner)) {
                    String replacement = RULES.get(call.name + call.desc);
                    if (replacement != null) {
                        call.owner = SHIM;
                        call.name = replacement;
                        changed++;
                    }
                }
            }
        }
        return changed;
    }
}
