package com.veeda.rewitchment.translator;

import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class CreatureTagsAndAttributesAdapter {
    public static final String ORIGINAL_PREFIX = "moriyashiine/bewitchment/";
    public static final String BEWITCHMENT =
            ORIGINAL_PREFIX + "common/Bewitchment";
    public static final String VAMPIRE =
            ORIGINAL_PREFIX + "common/entity/living/VampireEntity";
    public static final String WEREWOLF =
            ORIGINAL_PREFIX + "common/entity/living/WerewolfEntity";
    public static final String TRANSFORMATION =
            ORIGINAL_PREFIX + "api/component/TransformationComponent";
    public static final String FAMILIAR_MIXIN =
            ORIGINAL_PREFIX + "mixin/familiar/PlayerEntityMixin";

    private static final String LIVING = "net/minecraft/class_1309";
    private static final String GROUP = "net/minecraft/class_1310";
    private static final String OLD_GROUP_DESC = "()L" + GROUP + ";";
    private static final String LEGACY_TAGS =
            "com/veeda/rewitchment/probe/LegacyCreatureTypeTags";
    private static final String NATIVE_TAG_DESC = "(L" + LIVING + ";)Z";
    private static final String ATTR_INSTANCE = "net/minecraft/class_1324";
    private static final String ATTR_MODIFIER = "net/minecraft/class_1322";
    private static final String IDENTIFIER = "net/minecraft/class_2960";
    private static final String LEGACY_HAS_MODIFIER = "(L" + ATTR_MODIFIER + ";)Z";
    private static final String NATIVE_HAS_MODIFIER = "(L" + IDENTIFIER + ";)Z";
    private static final Map<String, String> VANILLA_GROUPS = Map.of(
            "field_6293", "isArthropod",  
            "field_6291", "isIllager"     
    );
    private static final Map<String, String> EXACT_ORIGINAL_GROUP_OWNERS = Map.of(
            BEWITCHMENT, "field_6293",
            VAMPIRE, "field_6291",
            WEREWOLF, "field_6291"
    );
    private static final Set<String> ORIGINAL_ATTRIBUTE_OWNERS =
            Set.of(TRANSFORMATION, FAMILIAR_MIXIN);

    public record Outcome(int arthropodComparisons, int illagerComparisons,
                          int nativeAttributeIdChecks) {}

    private CreatureTagsAndAttributesAdapter() {}

    public static Outcome rewrite(ClassNode cls) {
        if (!cls.name.startsWith(ORIGINAL_PREFIX)) return new Outcome(0, 0, 0);
        final String expectedGroup = EXACT_ORIGINAL_GROUP_OWNERS.get(cls.name);
        int arthropods = 0, illagers = 0, attributeIds = 0;
        for (MethodNode method : cls.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;) {
                AbstractInsnNode next = insn.getNext();
                if (insn instanceof MethodInsnNode call
                        && expectedGroup != null
                        && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && LIVING.equals(call.owner)
                        && "method_6046".equals(call.name)
                        && OLD_GROUP_DESC.equals(call.desc)
                        && next instanceof FieldInsnNode group
                        && group.getOpcode() == Opcodes.GETSTATIC
                        && GROUP.equals(group.owner)
                        && expectedGroup.equals(group.name)
                        && ("L" + GROUP + ";").equals(group.desc)
                        && next.getNext() instanceof JumpInsnNode branch
                        && (branch.getOpcode() == Opcodes.IF_ACMPNE
                        || branch.getOpcode() == Opcodes.IF_ACMPEQ)) {

                    String helperName = VANILLA_GROUPS.get(expectedGroup);
                    if (helperName == null) throw new IllegalStateException("Unsupported group field");
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = LEGACY_TAGS;
                    call.name = helperName;
                    call.desc = NATIVE_TAG_DESC;
                    call.itf = false;
                    method.instructions.remove(group);
                    JumpInsnNode unary = new JumpInsnNode(
                            branch.getOpcode() == Opcodes.IF_ACMPNE
                                    ? Opcodes.IFEQ : Opcodes.IFNE, branch.label);
                    method.instructions.insertBefore(branch, unary);
                    method.instructions.remove(branch);
                    if (expectedGroup.equals("field_6293")) arthropods++;
                    else illagers++;
                    next = unary.getNext();
                } else if (insn instanceof MethodInsnNode call
                        && ORIGINAL_ATTRIBUTE_OWNERS.contains(cls.name)
                        && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && ATTR_INSTANCE.equals(call.owner)
                        && "method_6196".equals(call.name)
                        && LEGACY_HAS_MODIFIER.equals(call.desc)) {

                    method.instructions.insertBefore(call, new MethodInsnNode(
                            Opcodes.INVOKEVIRTUAL, ATTR_MODIFIER, "comp_2447",
                            "()L" + IDENTIFIER + ";", false));
                    call.desc = NATIVE_HAS_MODIFIER;
                    attributeIds++;
                }
                insn = next;
            }
        }
        return new Outcome(arthropods, illagers, attributeIds);
    }
}
