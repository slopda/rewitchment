package com.veeda.rewitchment.translator;

import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

public final class RegistryFieldAdapter {
    public static final String ENTRY = "net/minecraft/class_6880";
    public static final String ENTRY_DESCRIPTOR = "L" + ENTRY + ";";

    private record Family(String valueOwner, Set<String> identifiers) {}

    private static final Map<String, Family> FAMILIES = Map.of(
            "net/minecraft/class_1294", new Family("net/minecraft/class_1291", Set.of(
                    "field_5898", "field_5899", "field_5901", "field_5902",
                    "field_5903", "field_5904", "field_5905", "field_5906",
                    "field_5907", "field_5909", "field_5910", "field_5911",
                    "field_5913", "field_5915", "field_5916", "field_5917",
                    "field_5918", "field_5919", "field_5920", "field_5923",
                    "field_5924", "field_5925"
            )),
            "net/minecraft/class_1847", new Family("net/minecraft/class_1842", Set.of(
                    "field_8973", "field_8980", "field_8991", "field_8999"
            )),
            "net/minecraft/class_5134", new Family("net/minecraft/class_1320", Set.of(
                    "field_23716", "field_23717", "field_23718", "field_23719",
                    "field_23720", "field_23721", "field_23723", "field_23724",
                    "field_23725"
            ))
    );

    private RegistryFieldAdapter() {}

    public static int rewrite(ClassNode node) {
        int changed = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode current = method.instructions.getFirst();
                 current != null;) {
                AbstractInsnNode next = current.getNext();
                if (current instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC) {
                    Family family = FAMILIES.get(field.owner);
                    if (family != null && family.identifiers().contains(field.name)
                            && field.desc.equals("L" + family.valueOwner() + ";")) {
                        field.desc = ENTRY_DESCRIPTOR;

                        MethodInsnNode value = new MethodInsnNode(
                                Opcodes.INVOKEINTERFACE, ENTRY, "comp_349",
                                "()Ljava/lang/Object;", true);
                        method.instructions.insert(field, value);
                        method.instructions.insert(value, new TypeInsnNode(
                                Opcodes.CHECKCAST, family.valueOwner()));
                        changed++;
                    }
                }
                current = next;
            }
        }
        return changed;
    }
}
